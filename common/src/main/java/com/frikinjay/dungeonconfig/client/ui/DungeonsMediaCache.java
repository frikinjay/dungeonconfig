package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.SafeDownloader;
import com.frikinjay.dungeonconfig.util.Threads;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonsMediaCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    private static final long DEADLINE_MILLIS = 15 * 60 * 1000L;

    @Nullable private static Path dir;
    private static final Map<String, CompletableFuture<Path>> DOWNLOADING = new ConcurrentHashMap<>();

    private DungeonsMediaCache() {}

    public static void configure(Path folder) {
        dir = folder;
    }

    public static boolean isConfigured() {
        return dir != null;
    }

    @Nullable
    public static Path cached(String url) {
        Path file = fileFor(url);
        return file != null && Files.isRegularFile(file) ? file : null;
    }

    public static CompletableFuture<Path> fetch(String url, long maxBytes) {
        Path file = fileFor(url);
        if (file == null) return CompletableFuture.failedFuture(new IllegalStateException("no cache folder"));
        if (Files.isRegularFile(file)) return CompletableFuture.completedFuture(file);
        // Registered before the download starts, so a download that finishes at once can still remove itself
        CompletableFuture<Path> result = new CompletableFuture<>();
        CompletableFuture<Path> running = DOWNLOADING.putIfAbsent(url, result);
        if (running != null) return running;
        SafeDownloader.fetchToFile(url, file, maxBytes, DEADLINE_MILLIS).whenComplete((downloaded, error) -> {
            DOWNLOADING.remove(url, result);
            if (error != null) {
                LOGGER.warn("Couldn't download {}: {}", url, Threads.rootMessage(error));
                result.completeExceptionally(error);
            } else {
                result.complete(file);
            }
        });
        return result;
    }

    public static void keepOnlyLinks(String... values) {
        List<String> links = new ArrayList<>();
        for (String v : values) {
            if (v != null && SafeDownloader.isUrl(v.trim())) links.add(v.trim());
        }
        keepOnly(links);
    }

    public static void keepOnly(Collection<String> urls) {
        Path folder = dir;
        if (folder == null || !Files.isDirectory(folder)) return;
        Set<String> keep = new HashSet<>();
        for (String url : urls) {
            Path file = fileFor(url);
            if (file != null) keep.add(file.getFileName().toString());
        }
        try (var list = Files.list(folder)) {
            for (Path file : (Iterable<Path>) list::iterator) {
                String name = file.getFileName().toString();
                if (keep.contains(name)) continue;
                if (name.endsWith(".part") && !DOWNLOADING.isEmpty()) continue;
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't tidy {}: {}", folder, e.toString());
        }
    }

    @Nullable
    private static Path fileFor(String url) {
        Path folder = dir;
        if (folder == null) return null;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(sha.digest(url.trim().getBytes(StandardCharsets.UTF_8))).substring(0, 32);
            return folder.resolve(hash + extension(url));
        } catch (Exception e) {
            return null;
        }
    }

    private static String extension(String url) {
        try {
            String path = URI.create(url.trim()).getPath();
            if (path == null) return "";
            int slash = path.lastIndexOf('/'), dot = path.lastIndexOf('.');
            if (dot <= slash || path.length() - dot > 6) return "";
            String ext = path.substring(dot).toLowerCase(Locale.ROOT);
            return ext.matches("\\.[a-z0-9]{1,5}") ? ext : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
