package com.frikinjay.dungeonconfig.util;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Small file helpers shared by Dungeon Config and mods built on it.
 */
public final class FileUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonConfig");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private FileUtil() {}

    /**
     * Moves {@code source} over {@code target}, atomically when the file system supports it.
     */
    public static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Writes text to a sibling {@code .tmp} file and moves it over {@code target}, so a crash
     * mid-write never leaves a half written file behind.
     */
    public static void writeStringAtomically(Path target, CharSequence text) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        moveReplacing(tmp, target);
    }

    /**
     * Copies an unreadable file to {@code <name>.bad-<timestamp>} next to it.
     *
     * @return the copy, or {@code null} if it couldn't be made
     */
    @Nullable
    public static Path backupBadFile(Path path) {
        Path copy = path.resolveSibling(path.getFileName() + ".bad-" + LocalDateTime.now().format(BACKUP_STAMP));
        try {
            Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
        } catch (IOException e) {
            LOGGER.error("Couldn't back up {}", path, e);
            return null;
        }
    }
}
