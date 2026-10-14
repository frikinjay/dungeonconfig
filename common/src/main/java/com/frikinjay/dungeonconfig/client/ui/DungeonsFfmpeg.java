package com.frikinjay.dungeonconfig.client.ui;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DungeonsFfmpeg {

    @Nullable private static Path cachedFfmpeg;
    private static List<Path> folders = List.of();
    private static long cachedAt;
    private static boolean cached;

    private DungeonsFfmpeg() {}

    public static synchronized void setFolders(List<Path> list) {
        if (list.equals(folders)) return;
        folders = List.copyOf(list);
        cached = false;
    }

    public static synchronized Path installFolder() {
        Path bin = folders.isEmpty() ? Path.of("ffmpeg") : folders.get(0);
        try {
            Files.createDirectories(bin);
        } catch (IOException ignored) {
        }
        return bin;
    }

    public static synchronized void forget() {
        cached = false;
    }

    public static boolean available() {
        return find() != null;
    }

    @Nullable
    public static synchronized Path find() {
        long now = System.nanoTime();
        if (cached && now - cachedAt < 30_000_000_000L) return cachedFfmpeg;
        cached = true;
        cachedAt = now;
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exe = windows ? "ffmpeg.exe" : "ffmpeg";
        List<Path> candidates = new ArrayList<>();
        for (Path folder : folders) {
            candidates.add(folder.resolve(exe));
            candidates.add(folder.resolve("bin").resolve(exe));
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) continue;
                try {
                    candidates.add(Path.of(dir.trim()).resolve(exe));
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (!windows) {
            candidates.add(Path.of("/opt/homebrew/bin/ffmpeg"));
            candidates.add(Path.of("/usr/local/bin/ffmpeg"));
            candidates.add(Path.of("/usr/bin/ffmpeg"));
        }
        cachedFfmpeg = null;
        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    cachedFfmpeg = candidate;
                    break;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return cachedFfmpeg;
    }

    public static List<String> input(String source, boolean remote, boolean loop) {
        List<String> args = new ArrayList<>();
        if (remote) {
            args.addAll(List.of("-protocol_whitelist", "http,https,tcp,tls,crypto", "-reconnect", "1", "-reconnect_streamed", "1",
                    "-rw_timeout", "15000000"));
        } else {
            args.addAll(List.of("-protocol_whitelist", "file"));
            if (loop) args.addAll(List.of("-stream_loop", "-1"));
        }
        args.addAll(List.of("-i", source));
        return args;
    }

    public static Process start(Path ffmpeg, List<String> args) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-nostdin"));
        cmd.addAll(args);
        Process p = new ProcessBuilder(cmd).start();
        p.getOutputStream().close();
        return p;
    }

    public static Thread drainErrors(Process p, String[] lastLine) {
        Thread t = new Thread(() -> {
            try (InputStream err = p.getErrorStream()) {
                String text = new String(err.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    String[] lines = text.split("\\R");
                    lastLine[0] = lines[lines.length - 1];
                }
            } catch (IOException ignored) {
            }
        }, "DungeonsUi FFmpeg errors");
        t.setDaemon(true);
        t.start();
        return t;
    }

    public static boolean hasExtension(String name, List<String> extensions) {
        String lower = name.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        int hash = lower.indexOf('#');
        if (hash >= 0) lower = lower.substring(0, hash);
        for (String ext : extensions) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
