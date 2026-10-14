package com.frikinjay.dungeonconfig.client.ui;

import com.frikinjay.dungeonconfig.util.FileUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DungeonsUiState {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");
    @Nullable private static Path file;
    private static JsonObject root = new JsonObject();
    private static boolean loaded;

    private DungeonsUiState() {}

    public static void configure(Path stateFile) {
        if (stateFile.equals(file)) return;
        file = stateFile;
        loaded = false;
    }

    public static boolean get(String key, boolean fallback) {
        load();
        JsonElement value = root.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static void set(String key, boolean value) {
        load();
        root.addProperty(key, value);
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        root = new JsonObject();
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("Ignoring {}: {}", file, e.toString());
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            FileUtil.writeStringAtomically(file, root.toString());
        } catch (Exception e) {
            LOGGER.warn("Couldn't save {}: {}", file, e.toString());
        }
    }
}
