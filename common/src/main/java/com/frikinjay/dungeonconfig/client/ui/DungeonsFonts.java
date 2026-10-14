package com.frikinjay.dungeonconfig.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DungeonsFonts {

    private static final Logger LOGGER = LoggerFactory.getLogger("DungeonsUi");

    public enum Role { TITLE, HEADING, BODY, VALUE }

    public record Source(String namespace, String path, int pixelSize) {}

    public record Place(@Nullable Path folder, Map<Role, List<Source>> resources) {
        public static Place folder(Path folder) {
            return new Place(folder, Map.of());
        }

        public static Place resources(Map<Role, List<Source>> resources) {
            return new Place(null, resources);
        }
    }

    static String namespace = "dungeonsui";
    private static List<Place> places = List.of();
    private static final Map<Role, DungeonsFont> fonts = new EnumMap<>(Role.class);
    @Nullable private static String loadedKey;
    private static boolean failed = false;

    private DungeonsFonts() {}

    public static void setNamespace(String ns) {
        namespace = ns;
    }

    public static void configure(List<Place> list) {
        places = List.copyOf(list);
    }

    public static void refresh() {
        String key = resolveKey();
        if (key.equals(loadedKey)) return;
        loadedKey = key;
        for (DungeonsFont f : fonts.values()) f.release();
        fonts.clear();
        failed = false;
        try {
            load();
        } catch (Throwable t) {
            fail(t);
        }
    }

    // Fallback to vanilla font if AWT font subsystem fails
    static void fail(Throwable t) {
        if (!failed) LOGGER.warn("Custom fonts disabled, the font system failed: {}", t.toString());
        failed = true;
        for (DungeonsFont f : fonts.values()) f.release();
        fonts.clear();
    }

    static void dropUnusedStrikes(int usedSince) {
        for (DungeonsFont f : new java.util.HashSet<>(fonts.values())) f.dropStrikesBefore(usedSince);
    }

    private static void load() {
        for (Role role : Role.values()) {
            DungeonsFont font = null;
            for (Place place : places) {
                font = place.folder() != null ? fromFolder(place.folder(), role, readOverrides(place.folder())) : fromResources(place, role);
                if (font != null) break;
            }
            if (font != null) fonts.put(role, font);
        }
        borrow(Role.VALUE, Role.TITLE);
        borrow(Role.TITLE, Role.VALUE);
        borrow(Role.HEADING, Role.BODY);
        borrow(Role.BODY, Role.HEADING);
        for (Role role : Role.values()) borrow(role, Role.BODY);
        for (Role role : Role.values()) borrow(role, Role.TITLE);

        DungeonsFont body = fonts.get(Role.BODY);
        for (DungeonsFont f : fonts.values()) {
            if (f != body) f.fallback = body;
        }
        failed = fonts.isEmpty();
    }

    private static void borrow(Role role, Role from) {
        if (!fonts.containsKey(role) && fonts.containsKey(from)) fonts.put(role, fonts.get(from));
    }

    public static DungeonsFont get(Role role) {
        if (loadedKey == null) refresh();
        return failed ? null : fonts.get(role);
    }

    private static String resolveKey() {
        StringBuilder key = new StringBuilder();
        for (Role role : Role.values()) {
            key.append(role).append('=');
            for (Place place : places) {
                String found = place.folder() != null ? folderFile(place.folder(), role, readOverrides(place.folder())) : resourceFile(place, role);
                if (found != null) {
                    key.append(found);
                    break;
                }
            }
            key.append(';');
        }
        for (Place place : places) {
            Path json = place.folder() == null ? null : place.folder().resolve("fonts.json");
            if (json != null && Files.isRegularFile(json)) key.append(stamp(json));
        }
        return key.toString();
    }

    private static String stamp(Path file) {
        try {
            return file + "@" + Files.getLastModifiedTime(file).toMillis() + "/" + Files.size(file);
        } catch (IOException | RuntimeException e) {
            return file.toString();
        }
    }

    @Nullable
    private static String folderFile(Path dir, Role role, JsonObject overrides) {
        Spec spec = spec(dir, role, overrides);
        return spec == null ? null : stamp(spec.path()) + "#" + spec.pixelSize();
    }

    @Nullable
    private static String resourceFile(Place place, Role role) {
        for (Source source : place.resources().getOrDefault(role, List.of())) {
            if (DungeonsCanvas.hasResource(source.namespace(), source.path())) return source.namespace() + ":" + source.path() + "#" + source.pixelSize();
        }
        return null;
    }

    private record Spec(Path path, String file, int pixelSize) {}

    @Nullable
    private static Spec spec(Path dir, Role role, JsonObject overrides) {
        String key = role.name().toLowerCase(Locale.ROOT);
        try {
            if (!Files.isDirectory(dir)) return null;
            String file = null;
            int pixelSize = 0;
            JsonElement spec = overrides.get(key);
            if (spec != null && spec.isJsonPrimitive()) {
                file = spec.getAsString();
            } else if (spec != null && spec.isJsonObject()) {
                JsonObject o = spec.getAsJsonObject();
                if (o.has("file")) file = o.get("file").getAsString();
                if (o.has("pixelSize")) pixelSize = o.get("pixelSize").getAsInt();
                if (o.has("smooth") && o.get("smooth").getAsBoolean()) pixelSize = -1;
            }
            if (file == null) {
                for (String ext : new String[]{".ttf", ".otf"}) {
                    if (Files.isRegularFile(dir.resolve(key + ext))) {
                        file = key + ext;
                        break;
                    }
                }
            }
            if (file == null) return null;
            Path path = dir.resolve(file).normalize();
            if (!path.startsWith(dir.normalize()) || !Files.isRegularFile(path)) return null;
            return new Spec(path, file, pixelSize);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject readOverrides(Path dir) {
        Path json = dir.resolve("fonts.json");
        if (!Files.isRegularFile(json)) return new JsonObject();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(json));
            return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            LOGGER.warn("Ignoring {}: {}", json, e.getMessage());
            return new JsonObject();
        }
    }

    @Nullable
    private static DungeonsFont fromFolder(Path dir, Role role, JsonObject overrides) {
        Spec spec = spec(dir, role, overrides);
        if (spec == null) return null;
        try {
            return DungeonsFont.load(spec.file(), Files.readAllBytes(spec.path()), spec.pixelSize());
        } catch (Throwable t) {
            LOGGER.warn("Could not load the {} font from {}: {}", role.name().toLowerCase(Locale.ROOT), dir, t.toString());
            return null;
        }
    }

    @Nullable
    private static DungeonsFont fromResources(Place place, Role role) {
        for (Source source : place.resources().getOrDefault(role, List.of())) {
            try (InputStream in = DungeonsCanvas.openResource(source.namespace(), source.path())) {
                if (in == null) continue;
                return DungeonsFont.load(source.namespace() + ":" + source.path(), in.readAllBytes(), source.pixelSize());
            } catch (Throwable t) {
                LOGGER.warn("Could not load font {}:{}: {}", source.namespace(), source.path(), t.toString());
            }
        }
        return null;
    }
}
