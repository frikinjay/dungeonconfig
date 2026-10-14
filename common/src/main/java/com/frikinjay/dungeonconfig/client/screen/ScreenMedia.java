package com.frikinjay.dungeonconfig.client.screen;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.api.Appearance;
import com.frikinjay.dungeonconfig.api.DungeonConfig;
import com.frikinjay.dungeonconfig.api.DungeonConfigs;
import com.frikinjay.dungeonconfig.client.ui.DungeonsBackground;
import com.frikinjay.dungeonconfig.client.ui.DungeonsCanvas;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFfmpeg;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Place;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Role;
import com.frikinjay.dungeonconfig.client.ui.DungeonsFonts.Source;
import com.frikinjay.dungeonconfig.client.ui.DungeonsMediaCache;
import com.frikinjay.dungeonconfig.client.ui.DungeonsMediaFiles;
import com.frikinjay.dungeonconfig.client.ui.DungeonsMusic;
import com.frikinjay.dungeonconfig.client.ui.DungeonsUiState;
import com.frikinjay.dungeonconfig.util.SafeDownloader;
import com.frikinjay.dungeonconfig.util.Threads;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

final class ScreenMedia {

    private static final long MAX_DEFAULT_BYTES = 512L * 1024 * 1024;
    private static final Set<String> PREPARED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService COPIER = Threads.daemonExecutor("DungeonConfig defaults");

    record Background(String spec, List<Path> folders, @Nullable Appearance from) {
        int blur() {
            return from == null ? 0 : from.blur().getLocalValue();
        }

        int dim() {
            return from == null ? 55 : from.dim().getLocalValue();
        }

        int fps() {
            return from != null && from.fps().getLocalValue() >= 45 ? 60 : 30;
        }
    }

    record Music(String spec, List<Path> folders, @Nullable Appearance from) {
        int volume() {
            return from == null ? 50 : from.volume().getLocalValue();
        }
    }

    private ScreenMedia() {}

    static DungeonConfig own() {
        return DungeonConfigMod.CONFIG;
    }

    static void setupShared() {
        DungeonsFonts.setNamespace(DungeonConfigMod.MOD_ID);
        Path shared = own().directory();
        DungeonsUiState.configure(shared.resolve("menu_state.json"));
        DungeonsMediaCache.configure(shared.resolve("cache").resolve("media"));
        List<Path> bins = new ArrayList<>();
        bins.add(shared.resolve("bin"));
        for (DungeonConfig config : DungeonConfigs.all()) {
            Path bin = config.directory().resolve("bin");
            if (!bins.contains(bin) && Files.isDirectory(bin)) bins.add(bin);
        }
        DungeonsFfmpeg.setFolders(bins);
    }

    static void tidyDownloads() {
        List<String> links = new ArrayList<>();
        for (DungeonConfig config : DungeonConfigs.all()) {
            Appearance a = config.appearance();
            if (a != null) {
                links.add(a.background().getLocalValue());
                links.add(a.music().getLocalValue());
            }
            if (config.defaultColors() != null && SafeDownloader.isUrl(config.defaultColors())) {
                links.add(config.defaultColors());
            }
        }
        DungeonsMediaCache.keepOnlyLinks(links.toArray(new String[0]));
    }

    static List<Path> backgroundFolders(DungeonConfig config) {
        return List.of(config.directory().resolve("bg"), defaultsDir(config).resolve("bg"));
    }

    static List<Path> musicFolders(DungeonConfig config) {
        return List.of(config.directory().resolve("bgmusic"), defaultsDir(config).resolve("bgmusic"));
    }

    private static Path defaultsDir(DungeonConfig config) {
        return config.directory().resolve("cache").resolve("defaults");
    }

    static Background background(DungeonConfig config) {
        Appearance a = config.appearance();
        DungeonConfig own = own();
        if (a == null) return config == own ? new Background("none", List.of(), null) : background(own);
        String value = a.background().getLocalValue().trim();
        List<Path> folders = backgroundFolders(config);
        if (config != own) {
            if (value.isEmpty() && !DungeonsBackground.hasFiles(folders)) return background(own);
            if (!value.isEmpty() && !DungeonsMediaFiles.isOff(value) && !SafeDownloader.isUrl(value)) folders = join(folders, backgroundFolders(own));
        }
        return new Background(value, folders, a);
    }

    static Music music(DungeonConfig config) {
        Appearance a = config.appearance();
        DungeonConfig own = own();
        if (a == null) return config == own ? new Music("none", List.of(), null) : music(own);
        String value = a.music().getLocalValue().trim();
        List<Path> folders = musicFolders(config);
        if (config != own) {
            if (value.isEmpty() && !DungeonsMusic.hasFiles(folders)) return music(own);
            if (!value.isEmpty() && !DungeonsMediaFiles.isOff(value) && !SafeDownloader.isUrl(value)) folders = join(folders, musicFolders(own));
        }
        return new Music(value, folders, a);
    }

    private static List<Path> join(List<Path> a, List<Path> b) {
        List<Path> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    static void configureFonts(DungeonConfig config) {
        DungeonConfig own = own();
        List<Place> places = new ArrayList<>();
        places.add(Place.folder(folder(config.directory().resolve("fonts"))));
        if (config != own) {
            places.add(Place.resources(modFonts(config.modId())));
            places.add(Place.folder(folder(own.directory().resolve("fonts"))));
        }
        places.add(Place.resources(modFonts(own.modId())));
        String ns = DungeonConfigMod.MOD_ID;
        List<Source> heavy = List.of(new Source(ns, "font/dungeons.ttf", 0), new Source(ns, "fonts/sanctum_pixel_bold.ttf", 0));
        List<Source> light = List.of(new Source(ns, "fonts/sanctum_pixel.ttf", 0));
        places.add(Place.resources(Map.of(Role.TITLE, heavy, Role.VALUE, heavy, Role.HEADING, light, Role.BODY, light)));
        DungeonsFonts.configure(places);
        DungeonsFonts.refresh();
    }

    private static Path folder(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    private static Map<Role, List<Source>> modFonts(String modId) {
        Map<Role, List<Source>> map = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            String name = "dungeonconfig/fonts/" + role.name().toLowerCase(Locale.ROOT);
            map.put(role, List.of(new Source(modId, name + ".ttf", 0), new Source(modId, name + ".otf", 0)));
        }
        return map;
    }

    static CompletableFuture<Void> prepareDefaults(DungeonConfig config) {
        if (!PREPARED.add(config.modId())) return CompletableFuture.completedFuture(null);
        String ns = config.modId();
        List<String> bg = direct(DungeonsCanvas.listResources(ns, "dungeonconfig/bg"), "dungeonconfig/bg");
        List<String> music = direct(DungeonsCanvas.listResources(ns, "dungeonconfig/bgmusic"), "dungeonconfig/bgmusic");
        Path base = defaultsDir(config);
        if (bg.isEmpty() && music.isEmpty() && !Files.isDirectory(base)) return CompletableFuture.completedFuture(null);
        String version = config.version();
        return CompletableFuture.runAsync(() -> {
            copy(ns, bg, base.resolve("bg"), version);
            copy(ns, music, base.resolve("bgmusic"), version);
        }, COPIER).exceptionally(e -> {
            DungeonConfigMod.LOGGER.warn("[DungeonConfig] Couldn't unpack the default menu media of {}: {}", ns, e.toString());
            return null;
        });
    }

    private static List<String> direct(List<String> paths, String folder) {
        List<String> out = new ArrayList<>();
        for (String path : paths) {
            String rest = path.substring(Math.min(path.length(), folder.length() + 1));
            if (!rest.isEmpty() && rest.indexOf('/') < 0) out.add(path);
        }
        return out;
    }

    private static void copy(String ns, List<String> paths, Path target, String version) {
        try {
            Path marker = target.resolve(".source");
            String stamp = version + "\n" + String.join("\n", paths);
            Set<String> names = new HashSet<>();
            for (String path : paths) names.add(path.substring(path.lastIndexOf('/') + 1));
            if (Files.isRegularFile(marker) && stamp.equals(Files.readString(marker, StandardCharsets.UTF_8))
                    && names.stream().allMatch(n -> Files.isRegularFile(target.resolve(n)))) {
                return;
            }
            if (paths.isEmpty()) {
                if (Files.isDirectory(target)) clear(target, Set.of());
                return;
            }
            Files.createDirectories(target);
            clear(target, names);
            for (String path : paths) {
                Path out = target.resolve(path.substring(path.lastIndexOf('/') + 1));
                Path part = out.resolveSibling(out.getFileName() + ".part");
                try (InputStream in = DungeonsCanvas.openResource(ns, path)) {
                    if (in == null) continue;
                    long written = 0;
                    try (OutputStream o = Files.newOutputStream(part)) {
                        byte[] buf = new byte[1 << 16];
                        for (int n; (n = in.read(buf)) > 0; ) {
                            written += n;
                            if (written > MAX_DEFAULT_BYTES) throw new IOException(path + " is too large");
                            o.write(buf, 0, n);
                        }
                    }
                    Files.move(part, out, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.deleteIfExists(part);
                    DungeonConfigMod.LOGGER.warn("[DungeonConfig] Couldn't unpack {}:{}: {}", ns, path, e.getMessage());
                }
            }
            Files.writeString(marker, stamp, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DungeonConfigMod.LOGGER.warn("[DungeonConfig] Couldn't unpack the default menu media of {} into {}: {}", ns, target, e.getMessage());
        }
    }

    private static void clear(Path dir, Set<String> keep) throws IOException {
        try (var list = Files.list(dir)) {
            for (Path file : (Iterable<Path>) list::iterator) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && !keep.contains(name)) Files.deleteIfExists(file);
            }
        }
    }

    static boolean needsFfmpeg(DungeonConfig config) {
        Background bg = background(config);
        Music music = music(config);
        return DungeonsBackground.needsFfmpeg(bg.spec(), bg.folders()) || DungeonsMusic.needsFfmpeg(music.spec(), music.folders());
    }
}
