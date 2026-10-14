package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.SafeDownloader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Helpers shared by the menu background, music and colour settings for finding their files.
 */
public final class DungeonsMediaFiles {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    private static final Random RANDOM = new Random();

    private DungeonsMediaFiles() {}

    /**
     * Whether a media setting was turned off with {@code none} or {@code off}.
     */
    public static boolean isOff(@Nullable String value) {
        if (value == null) return false;
        String v = value.trim();
        return v.equalsIgnoreCase("none") || v.equalsIgnoreCase("off");
    }

    /**
     * Lists the regular files directly in {@code folder} whose lower case name passes {@code accept}, sorted by name.
     */
    public static List<Path> list(Path folder, Predicate<String> accept) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(folder)) return files;
        try (var list = Files.list(folder)) {
            for (Path file : (Iterable<Path>) list::iterator) {
                if (Files.isRegularFile(file) && accept.test(file.getFileName().toString().toLowerCase(Locale.ROOT))) files.add(file);
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't list {}: {}", folder, e.toString());
        }
        files.sort(null);
        return files;
    }

    /**
     * Finds a file named by the player in the first folder that has it, refusing names that
     * point outside those folders.
     *
     * @param what what the file is for, used in log messages
     */
    @Nullable
    public static Path named(List<Path> folders, String value, String what) {
        for (Path folder : folders) {
            try {
                Path file = folder.resolve(value).normalize();
                if (!file.startsWith(folder.normalize())) {
                    LOGGER.warn("{} \"{}\" is outside {}, ignoring it", what, value, folder);
                    return null;
                }
                if (Files.isRegularFile(file)) return file;
            } catch (RuntimeException e) {
                LOGGER.warn("{} \"{}\" isn't a usable file name: {}", what, value, e.getMessage());
                return null;
            }
        }
        LOGGER.warn("{} \"{}\" wasn't found in {}", what, value, folders.isEmpty() ? "any folder" : folders.get(0));
        return null;
    }

    /**
     * Whether a download is a plain text list with one link per line.
     */
    public static boolean isTextUrlList(byte[] data) {
        if (data.length == 0 || data.length > 1024 * 1024) return false;
        for (byte b : data) {
            if (b == 0) return false;
        }
        String text = new String(data, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) return false;
        boolean anyUrl = false;
        for (String line : text.split("\\R")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!SafeDownloader.isUrl(line)) return false;
            anyUrl = true;
        }
        return anyUrl;
    }

    /**
     * Picks one of the links in a text list at random.
     */
    @Nullable
    public static String pickRandomUrl(byte[] data) {
        List<String> urls = new ArrayList<>();
        for (String line : new String(data, StandardCharsets.UTF_8).split("\\R")) {
            line = line.trim();
            if (!line.isEmpty() && SafeDownloader.isUrl(line)) urls.add(line);
        }
        return urls.isEmpty() ? null : urls.get(RANDOM.nextInt(urls.size()));
    }
}
