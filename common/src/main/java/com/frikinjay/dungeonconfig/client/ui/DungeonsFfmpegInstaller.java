package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.FileUtil;
import com.frikinjay.dungeonconfig.util.SafeDownloader;
import com.frikinjay.dungeonconfig.util.Threads;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DungeonsFfmpegInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    private static final long MAX_ARCHIVE = 400L * 1024 * 1024;
    private static final long MAX_PROGRAM = 400L * 1024 * 1024;
    private static final long DEADLINE_MILLIS = 30 * 60 * 1000L;

    public record Build(String url, @Nullable String checksums, @Nullable String checksumName, String provider, int approxMb, String program) {}

    public enum State { IDLE, DOWNLOADING, INSTALLING, DONE, FAILED }

    private static volatile State state = State.IDLE;
    private static final AtomicLong received = new AtomicLong();
    private static volatile int expectedMb;
    @Nullable private static volatile String failure;

    private DungeonsFfmpegInstaller() {}

    @Nullable
    public static Build forThisSystem() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.contains("amd64") || arch.contains("x86_64") || arch.equals("x64");
        if (os.contains("win")) {
            String name = arm ? "ffmpeg-master-latest-winarm64-gpl.zip" : x64 ? "ffmpeg-master-latest-win64-gpl.zip" : null;
            if (name == null) return null;
            String base = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/";
            return new Build(base + name, base + "checksums.sha256", name, "BtbN/FFmpeg-Builds on GitHub", 200, "ffmpeg.exe");
        }
        String system = os.contains("mac") || os.contains("darwin") ? "macos" : os.contains("linux") ? "linux" : null;
        String cpu = arm ? "arm64" : x64 ? "amd64" : null;
        if (system == null || cpu == null) return null;
        String url = "https://ffmpeg.martin-riedl.de/redirect/latest/" + system + "/" + cpu + "/release/ffmpeg.zip";
        return new Build(url, url + ".sha256", null, "Martin Riedl (ffmpeg.martin-riedl.de)", 80, "ffmpeg");
    }

    public static State state() {
        return state;
    }

    public static float progress() {
        if (state != State.DOWNLOADING) return state == State.IDLE ? 0 : 1;
        return Math.min(0.99F, received.get() / (expectedMb * 1024F * 1024F));
    }

    public static long receivedMb() {
        return received.get() / (1024 * 1024);
    }

    @Nullable
    public static String failure() {
        return failure;
    }

    public static synchronized CompletableFuture<Path> install(Path bin) {
        Build build = forThisSystem();
        if (build == null) return CompletableFuture.failedFuture(new IllegalStateException("no FFmpeg build for this system"));
        if (state == State.DOWNLOADING || state == State.INSTALLING) return CompletableFuture.failedFuture(new IllegalStateException("already downloading"));
        state = State.DOWNLOADING;
        received.set(0);
        expectedMb = build.approxMb();
        failure = null;
        Path archive = bin.resolve("ffmpeg-download.zip");
        LOGGER.info("Downloading FFmpeg from {}", build.url());
        return SafeDownloader.fetchToFile(build.url(), archive, MAX_ARCHIVE, DEADLINE_MILLIS, received)
                .thenApplyAsync(result -> {
                    state = State.INSTALLING;
                    try {
                        verify(build, archive);
                        Path program = extract(archive, bin, build.program());
                        LOGGER.info("FFmpeg installed to {}", program);
                        return program;
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
                    } finally {
                        try {
                            Files.deleteIfExists(archive);
                        } catch (Exception ignored) {
                        }
                    }
                })
                .whenComplete((program, error) -> {
                    if (error == null) {
                        DungeonsFfmpeg.forget();
                        state = State.DONE;
                    } else {
                        failure = Threads.rootMessage(error);
                        LOGGER.warn("Couldn't install FFmpeg: {}", failure);
                        state = State.FAILED;
                    }
                });
    }

    // Installs only a download whose SHA-256 matches the checksum its provider publishes
    private static void verify(Build build, Path archive) throws Exception {
        if (build.checksums() == null) throw new SecurityException("the provider publishes no checksum, it was not installed");
        String listing;
        try {
            listing = new String(SafeDownloader.fetch(build.checksums()).get().body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("the published checksum couldn't be downloaded (" + Threads.rootMessage(e) + "), it was not installed", e);
        }
        String expected = null;
        for (String line : listing.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0 || !parts[0].matches("(?i)[0-9a-f]{64}")) continue;
            if (build.checksumName() == null || (parts.length > 1 && parts[parts.length - 1].replace("*", "").endsWith(build.checksumName()))) {
                expected = parts[0].toLowerCase(Locale.ROOT);
                break;
            }
        }
        if (expected == null) throw new SecurityException("the published checksums don't list the download, it was not installed");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(archive)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) sha.update(buf, 0, n);
        }
        String actual = HexFormat.of().formatHex(sha.digest());
        if (!actual.equals(expected)) throw new SecurityException("the download doesn't match its published checksum, it was not installed");
    }

    private static Path extract(Path archive, Path bin, String program) throws Exception {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry found = null;
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                String file = name.substring(name.lastIndexOf('/') + 1);
                if (file.equalsIgnoreCase(program)) {
                    found = entry;
                    break;
                }
            }
            if (found == null) throw new IllegalStateException("the archive has no " + program + " in it");
            Path target = bin.resolve(program);
            Path part = bin.resolve(program + ".part");
            long written = 0;
            try (InputStream in = zip.getInputStream(found); OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) {
                    written += n;
                    if (written > MAX_PROGRAM) throw new IllegalStateException("the program in the archive is unexpectedly large");
                    out.write(buf, 0, n);
                }
            } catch (Exception e) {
                Files.deleteIfExists(part);
                throw e;
            }
            FileUtil.moveReplacing(part, target);
            if (!target.toFile().setExecutable(true, false)) target.toFile().setExecutable(true);
            return target;
        }
    }
}
