package com.frikinjay.dungeonconfig.api;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.network.ConfigSync;
import com.frikinjay.dungeonconfig.platform.DcPlatform;
import com.frikinjay.dungeonconfig.util.FileUtil;
import com.frikinjay.dungeonconfig.util.Json5;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One mod's configuration: its {@link ConfigEntry entries}, the JSON5 file they are saved to,
 * its settings screen and the values a server sends to players.
 * <p>
 * Each mod makes exactly one {@code DungeonConfig}, normally in a static field of a class in
 * common code. {@link Builder#build()} registers it with {@link DungeonConfigs}, which puts the
 * mod on the Dungeon Configs page and gives it a settings screen.
 * <p>
 * Entries are added with the {@code add*} methods, then {@link #load()} reads
 * {@code config/<modid>/<modid>.json5}, creating it with the defaults if it doesn't exist yet.
 * Read values from the entries with {@link ConfigEntry#get()}; they are kept up to date when the
 * player saves the settings screen.
 *
 * <h2>Defining a config with the add methods</h2>
 * <pre>{@code
 * public final class MyModConfig {
 *     public static final DungeonConfig CONFIG = DungeonConfig.builder("mymod")
 *             .displayName("My Mod")
 *             .build();
 *
 *     public static final ConfigEntry<Boolean> PARTICLES =
 *             CONFIG.addBoolean("particlesEnabled", "Visuals", true, "Show custom particles");
 *     public static final ConfigEntry<Integer> AURA_RADIUS =
 *             CONFIG.addInt("auraRadius", "Gameplay", 16, 1, 64, "Radius of the player's aura in blocks");
 *
 *     // Call once from your mod initializer, after the fields above exist
 *     public static void init() {
 *         CONFIG.load();
 *     }
 * }
 *
 * // Anywhere in your mod
 * if (MyModConfig.PARTICLES.get()) spawnParticles(MyModConfig.AURA_RADIUS.get());
 * }</pre>
 *
 * <h2>Defining a config with a NeoForge style spec</h2>
 * <pre>{@code
 * ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
 * builder.push("Combat");
 * ModConfigSpec.DoubleValue CRIT = builder.comment("Critical hit damage multiplier")
 *         .defineInRange("critMultiplier", 1.5, 1.0, 5.0);
 * builder.pop();
 *
 * DungeonConfig config = DungeonConfig.builder("mymod")
 *         .spec(builder.build())
 *         .build();
 * config.load();
 * }</pre>
 *
 * <h2>The file on disk</h2>
 * Every entry is written with its comment above it, in the order the entries were added
 * (see {@link #setOrder(Collection)}):
 * <pre>
 * // My Mod configuration
 * // This file uses JSON5 syntax (https://json5.org/)
 * {
 *   // Show custom particles
 *   "particlesEnabled": true,
 *
 *   // Radius of the player's aura in blocks
 *   "auraRadius": 16
 * }
 * </pre>
 */
public final class DungeonConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String modId;
    @Nullable private final String displayName;
    private final String fileName;
    @Nullable private final String appearanceCategory;
    private final String defaultBackground, defaultMusic, defaultColors;
    private final int defaultBlur, defaultDim, defaultFps, defaultVolume;
    private final boolean fireflies;
    private final Map<String, ConfigEntry<?>> entries = new LinkedHashMap<>();
    private final List<Runnable> saveListeners = new CopyOnWriteArrayList<>();
    @Nullable private Appearance appearance;
    @Nullable private Path directory;
    private boolean loaded;

    private DungeonConfig(Builder b) {
        this.modId = b.modId;
        this.displayName = b.displayName;
        this.fileName = b.fileName != null ? b.fileName : b.modId + ".json5";
        this.appearanceCategory = b.appearance ? b.appearanceCategory : null;
        this.defaultBackground = b.defaultBackground;
        this.defaultMusic = b.defaultMusic;
        this.defaultColors = b.defaultColors;
        this.defaultBlur = b.defaultBlur;
        this.defaultDim = b.defaultDim;
        this.defaultFps = b.defaultFps;
        this.defaultVolume = b.defaultVolume;
        this.fireflies = b.fireflies;
    }

    /**
     * Starts building the config of a mod.
     * <pre>{@code
     * DungeonConfig config = DungeonConfig.builder("mymod").build();
     * }</pre>
     *
     * @param modId the mod's id, which must match {@code [a-z0-9_.-]+}; it names the config
     *              folder and file, and finds the mod's name, icon and description in its metadata
     * @return a new builder
     * @throws IllegalArgumentException if {@code modId} isn't a valid mod id
     */
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    /**
     * Builds a {@link DungeonConfig}. Every setting is optional.
     * <pre>{@code
     * DungeonConfig config = DungeonConfig.builder("mymod")
     *         .displayName("My Mod")
     *         .appearanceCategory("Interface")
     *         .defaultBackground("https://example.com/backgrounds.txt")
     *         .defaultBackgroundBlur(20)
     *         .defaultMusicVolume(40)
     *         .build();
     * }</pre>
     */
    public static final class Builder {
        private final String modId;
        @Nullable private String displayName;
        @Nullable private String fileName;
        @Nullable private ModConfigSpec spec;
        private boolean appearance = true;
        private String appearanceCategory = "Appearance";
        private String defaultBackground = "", defaultMusic = "", defaultColors = "";
        private int defaultBlur = 0, defaultDim = 55, defaultFps = 30, defaultVolume = 30;
        private boolean fireflies = true;

        private Builder(String modId) {
            if (!modId.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Not a valid mod id: " + modId);
            this.modId = modId;
        }

        /**
         * Sets the name shown on the settings screen and the Dungeon Configs page.
         * Without it the name from the mod's metadata is used, or the mod id if there is none.
         * <pre>{@code
         * DungeonConfig.builder("mymod").displayName("My Mod").build();
         * }</pre>
         *
         * @param name the name players see
         * @return this builder
         */
        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        /**
         * Changes the name of the config file inside {@code config/<modid>}.
         * Defaults to {@code <modid>.json5}.
         * <pre>{@code
         * // Saved to config/mymod/client.json5
         * DungeonConfig.builder("mymod").fileName("client.json5").build();
         * }</pre>
         *
         * @param name the file name, including its extension
         * @return this builder
         */
        public Builder fileName(String name) {
            this.fileName = name;
            return this;
        }

        /**
         * Adds every entry defined in a {@link ModConfigSpec} to the config, in the order they were defined.
         * Entries can still be added with the {@code add*} methods afterwards.
         * <pre>{@code
         * ModConfigSpec.Builder spec = new ModConfigSpec.Builder();
         * ModConfigSpec.BooleanValue FAST = spec.define("General.fastMode", false);
         * DungeonConfig config = DungeonConfig.builder("mymod").spec(spec.build()).build();
         * }</pre>
         *
         * @param spec the built spec
         * @return this builder
         */
        public Builder spec(ModConfigSpec spec) {
            this.spec = spec;
            return this;
        }

        /**
         * Sets the tab that holds the settings screen's own background and music options.
         * Defaults to {@code "Appearance"}.
         * <pre>{@code
         * DungeonConfig.builder("mymod").appearanceCategory("Interface").build();
         * }</pre>
         *
         * @param category the tab name; use the name of one of your own tabs to put the options there
         * @return this builder
         */
        public Builder appearanceCategory(String category) {
            this.appearanceCategory = category;
            return this;
        }

        /**
         * Sets the default for the settings screen background. Players can change it in game.
         * <p>
         * Accepts a file name in {@code config/<modid>/bg}, a link to a picture, GIF or video,
         * a link to a {@code .txt} list of links (one is picked at random), {@code "none"} for no
         * background, or an empty string (the default) for a random file from the {@code bg} folder.
         * Files a mod ships in {@code assets/<modid>/dungeonconfig/bg} are copied into the folder used
         * for the random pick. Videos need FFmpeg.
         * <pre>{@code
         * DungeonConfig.builder("mymod")
         *         .defaultBackground("https://example.com/castle.png")
         *         .build();
         * }</pre>
         *
         * @param value the default background
         * @return this builder
         */
        public Builder defaultBackground(String value) {
            this.defaultBackground = value;
            return this;
        }

        /**
         * Sets the default music played while the settings screen is open. Players can change it in game.
         * <p>
         * Accepts a file name in {@code config/<modid>/bgmusic}, a link to an audio file, a link to a
         * {@code .txt} list of links, {@code "none"} for silence, or an empty string (the default) for
         * random tracks from the {@code bgmusic} folder. Formats other than ogg and wav need FFmpeg.
         * <pre>{@code
         * DungeonConfig.builder("mymod").defaultMusic("none").build();
         * }</pre>
         *
         * @param value the default music
         * @return this builder
         */
        public Builder defaultMusic(String value) {
            this.defaultMusic = value;
            return this;
        }

        /**
         * Sets where the screen's colours come from when the mod has no {@code colors.json5} yet.
         * Accepts a link to a colours file or to a {@code .txt} list of such links. A mod can also ship
         * {@code assets/<modid>/dungeonconfig/colors.json5}, which is copied into its config folder.
         * <pre>{@code
         * DungeonConfig.builder("mymod")
         *         .defaultColors("https://example.com/mymod-colors.json5")
         *         .build();
         * }</pre>
         *
         * @param value a link, or an empty string for the built in colours
         * @return this builder
         */
        public Builder defaultColors(String value) {
            this.defaultColors = value;
            return this;
        }

        /**
         * Sets how blurred the background is by default, from 0 (sharp) to 100. Out of range values are clamped.
         * <pre>{@code
         * DungeonConfig.builder("mymod").defaultBackgroundBlur(25).build();
         * }</pre>
         *
         * @param value blur percentage
         * @return this builder
         */
        public Builder defaultBackgroundBlur(int value) {
            this.defaultBlur = Math.max(0, Math.min(100, value));
            return this;
        }

        /**
         * Sets how much the background is darkened by default, from 0 to 100. Defaults to 55.
         * Out of range values are clamped.
         * <pre>{@code
         * DungeonConfig.builder("mymod").defaultBackgroundDim(70).build();
         * }</pre>
         *
         * @param value dim percentage
         * @return this builder
         */
        public Builder defaultBackgroundDim(int value) {
            this.defaultDim = Math.max(0, Math.min(100, value));
            return this;
        }

        /**
         * Sets the default frame rate of video and GIF backgrounds. Values of 45 or more mean 60 FPS,
         * anything lower means 30 FPS (the default).
         * <pre>{@code
         * DungeonConfig.builder("mymod").defaultBackgroundFps(60).build();
         * }</pre>
         *
         * @param value the wanted frame rate
         * @return this builder
         */
        public Builder defaultBackgroundFps(int value) {
            this.defaultFps = value >= 45 ? 60 : 30;
            return this;
        }

        /**
         * Sets the default music volume, from 0 to 100. Defaults to 30. Out of range values are clamped.
         * <pre>{@code
         * DungeonConfig.builder("mymod").defaultMusicVolume(50).build();
         * }</pre>
         *
         * @param value volume percentage
         * @return this builder
         */
        public Builder defaultMusicVolume(int value) {
            this.defaultVolume = Math.max(0, Math.min(100, value));
            return this;
        }

        /**
         * Leaves out the background and music options. The screen then uses Dungeon Config's own
         * background and music settings, and {@link DungeonConfig#appearance()} returns {@code null}.
         * <pre>{@code
         * DungeonConfig.builder("mymod").noAppearance().build();
         * }</pre>
         *
         * @return this builder
         */
        public Builder noAppearance() {
            this.appearance = false;
            return this;
        }

        /**
         * Turns off the floating firefly and light streak effects on this mod's settings screen.
         * <pre>{@code
         * DungeonConfig.builder("mymod").noFireflies().build();
         * }</pre>
         *
         * @return this builder
         */
        public Builder noFireflies() {
            this.fireflies = false;
            return this;
        }

        /**
         * Creates the config, adds the entries of the {@link #spec(ModConfigSpec) spec} if one was given,
         * and registers it with {@link DungeonConfigs}. The config isn't read from disk until
         * {@link DungeonConfig#load()} is called.
         * <pre>{@code
         * public static final DungeonConfig CONFIG = DungeonConfig.builder("mymod").build();
         * }</pre>
         *
         * @return the new config
         * @throws IllegalStateException if a config for this mod id was already built
         */
        public DungeonConfig build() {
            DungeonConfig config = new DungeonConfig(this);
            if (spec != null) {
                spec.bind(config);
                for (ConfigEntry<?> entry : spec.getEntries()) {
                    config.add(entry);
                }
            }
            DungeonConfigs.register(config);
            return config;
        }
    }

    /**
     * Adds an entry, for options the {@code add*} shortcuts don't cover, such as enums, lists,
     * dropdowns, units or synced values.
     * <p>
     * Add every entry before calling {@link #load()}. An entry added later keeps its default until
     * the file is next read, and a warning is logged.
     * <pre>{@code
     * public static final ConfigEntry<Difficulty> MODE = CONFIG.add(
     *         new ConfigEntry<>("mode", "How hard mobs hit", "Gameplay", Difficulty.NORMAL, Difficulty.class));
     *
     * public static final ConfigEntry<Integer> RANGE = CONFIG.add(
     *         new ConfigEntry<>("rangeBlocks", "Search range", "Gameplay", 8, Integer.class, 1, 32)
     *                 .withUnit(" blocks")
     *                 .synced());
     * }</pre>
     *
     * @param entry the entry to add
     * @param <T>   the entry's value type
     * @return the same entry, so it can be stored in a field in one statement
     * @throws IllegalArgumentException if this config already has an entry with that name,
     *                                  or the entry was already added to another config
     */
    public synchronized <T> ConfigEntry<T> add(ConfigEntry<T> entry) {
        if (entries.containsKey(entry.getName())) {
            throw new IllegalArgumentException("Duplicate config entry " + entry.getName() + " in " + modId);
        }
        if (entry.owner != null) throw new IllegalArgumentException("Config entry " + entry.getName() + " already belongs to " + entry.owner.modId);
        entry.owner = this;
        entries.put(entry.getName(), entry);
        if (loaded) DungeonConfigMod.LOGGER.warn("[DungeonConfig] {} was added to {} after it loaded, its saved value is only read on the next reload", entry.getName(), modId);
        return entry;
    }

    /**
     * Adds an on/off option, shown as a toggle.
     * <pre>{@code
     * public static final ConfigEntry<Boolean> PARTICLES =
     *         CONFIG.addBoolean("particlesEnabled", "Visuals", true, "Show custom particles");
     * }</pre>
     *
     * @param name         the key in the file; also used to find translations and make the label
     * @param category     the tab the option appears in
     * @param defaultValue the value used until the player changes it
     * @param comment      the description shown on the screen and written above the value in the file
     * @return the new entry
     */
    public ConfigEntry<Boolean> addBoolean(String name, String category, boolean defaultValue, String comment) {
        return add(new ConfigEntry<>(name, comment, category, defaultValue, Boolean.class));
    }

    /**
     * Adds a whole number option, shown as a slider. Values read from the file are clamped to the range.
     * <pre>{@code
     * public static final ConfigEntry<Integer> MAX_HOMES =
     *         CONFIG.addInt("maxHomes", "Teleports", 3, 1, 100, "Homes each player can set");
     * }</pre>
     *
     * @param name         the key in the file
     * @param category     the tab the option appears in
     * @param defaultValue the value used until the player changes it
     * @param min          the lowest allowed value
     * @param max          the highest allowed value
     * @param comment      the description shown on the screen and written in the file
     * @return the new entry
     */
    public ConfigEntry<Integer> addInt(String name, String category, int defaultValue, int min, int max, String comment) {
        return add(new ConfigEntry<>(name, comment, category, defaultValue, Integer.class, min, max));
    }

    /**
     * Adds a long option, shown as a slider. Values read from the file are clamped to the range.
     * <pre>{@code
     * public static final ConfigEntry<Long> SEED_OFFSET =
     *         CONFIG.addLong("seedOffset", "World", 0L, 0L, 1_000_000L, "Added to the structure seed");
     * }</pre>
     *
     * @param name         the key in the file
     * @param category     the tab the option appears in
     * @param defaultValue the value used until the player changes it
     * @param min          the lowest allowed value
     * @param max          the highest allowed value
     * @param comment      the description shown on the screen and written in the file
     * @return the new entry
     */
    public ConfigEntry<Long> addLong(String name, String category, long defaultValue, long min, long max, String comment) {
        return add(new ConfigEntry<>(name, comment, category, defaultValue, Long.class, min, max));
    }

    /**
     * Adds a decimal option, shown as a slider that moves in steps of 0.1 unless
     * {@link ConfigEntry#withStep(double)} says otherwise. Values read from the file are clamped to the range.
     * <pre>{@code
     * public static final ConfigEntry<Double> ZOOM =
     *         CONFIG.addDouble("zoomLevel", "Zoom", 3.0, 1.0, 50.0, "Zoom when you start zooming").withUnit("x");
     * }</pre>
     *
     * @param name         the key in the file
     * @param category     the tab the option appears in
     * @param defaultValue the value used until the player changes it
     * @param min          the lowest allowed value
     * @param max          the highest allowed value
     * @param comment      the description shown on the screen and written in the file
     * @return the new entry
     */
    public ConfigEntry<Double> addDouble(String name, String category, double defaultValue, double min, double max, String comment) {
        return add(new ConfigEntry<>(name, comment, category, defaultValue, Double.class, min, max));
    }

    /**
     * Adds a text option, shown as a text box.
     * <pre>{@code
     * public static final ConfigEntry<String> GREETING =
     *         CONFIG.addString("greeting", "Chat", "Welcome!", "Message sent to players when they join");
     * }</pre>
     *
     * @param name         the key in the file
     * @param category     the tab the option appears in
     * @param defaultValue the value used until the player changes it
     * @param comment      the description shown on the screen and written in the file
     * @return the new entry
     */
    public ConfigEntry<String> addString(String name, String category, String defaultValue, String comment) {
        return add(new ConfigEntry<>(name, comment, category, defaultValue, String.class));
    }

    /**
     * Changes the order entries are shown on the screen and written to the file. Entries in
     * {@code order} come first, in that order; entries left out keep their place after them.
     * Entries that don't belong to this config are ignored.
     * <pre>{@code
     * // Show the master switch before everything else
     * CONFIG.setOrder(List.of(ENABLED, RANGE));
     * }</pre>
     *
     * @param order the entries to move to the front
     */
    public synchronized void setOrder(Collection<? extends ConfigEntry<?>> order) {
        Map<String, ConfigEntry<?>> rebuilt = new LinkedHashMap<>();
        for (ConfigEntry<?> entry : order) {
            if (entries.get(entry.getName()) == entry) rebuilt.put(entry.getName(), entry);
        }
        for (ConfigEntry<?> entry : entries.values()) rebuilt.putIfAbsent(entry.getName(), entry);
        entries.clear();
        entries.putAll(rebuilt);
    }

    /**
     * Returns a snapshot of every entry, in display order. Later additions don't show up in it.
     * <pre>{@code
     * for (ConfigEntry<?> entry : CONFIG.entries()) {
     *     LOGGER.info("{} = {}", entry.getName(), entry.get());
     * }
     * }</pre>
     *
     * @return the entries, which can't be modified
     */
    public synchronized Collection<ConfigEntry<?>> entries() {
        return Collections.unmodifiableCollection(new ArrayList<>(entries.values()));
    }

    /**
     * Finds an entry by its name.
     * <pre>{@code
     * ConfigEntry<?> entry = CONFIG.entry("auraRadius");
     * if (entry != null) entry.resetToDefault();
     * }</pre>
     *
     * @param name the entry's key in the file
     * @return the entry, or {@code null} if there is none with that name
     */
    @Nullable
    public synchronized ConfigEntry<?> entry(String name) {
        return entries.get(name);
    }

    /**
     * Returns the tab names used by the entries, in the order they first appear.
     * <pre>{@code
     * List<String> tabs = CONFIG.categories(); // e.g. [General, Visuals, Appearance]
     * }</pre>
     *
     * @return the tab names
     */
    public synchronized List<String> categories() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (ConfigEntry<?> entry : entries.values()) categories.add(entry.getCategory());
        return new ArrayList<>(categories);
    }

    /**
     * Returns the settings screen's background and music options. They are created the first time
     * this, {@link #load()} or {@link #save()} is called, so they come after the mod's own entries.
     * <pre>{@code
     * Appearance looks = CONFIG.appearance();
     * if (looks != null) looks.volume().set(0);
     * }</pre>
     *
     * @return the options, or {@code null} if the builder used {@link Builder#noAppearance()}
     */
    @Nullable
    public synchronized Appearance appearance() {
        ensureAppearance();
        return appearance;
    }

    private void ensureAppearance() {
        if (appearance != null || appearanceCategory == null) return;
        appearance = Appearance.create(this, appearanceCategory, defaultBackground, defaultMusic,
                defaultBlur, defaultDim, defaultFps, defaultVolume);
    }

    /**
     * Returns the mod id this config was built for.
     * <pre>{@code
     * // The screen title can be translated with this key
     * String titleKey = CONFIG.modId() + ".config.title";
     * }</pre>
     *
     * @return the mod id
     */
    public String modId() {
        return modId;
    }

    /**
     * Returns the name shown for the mod: the one given to {@link Builder#displayName(String)},
     * else the name in the mod's metadata, else the mod id.
     * <pre>{@code
     * player.sendSystemMessage(Component.literal(CONFIG.displayName() + " settings reloaded"));
     * }</pre>
     *
     * @return the display name
     */
    public String displayName() {
        if (displayName != null) return displayName;
        String name = DcPlatform.modName(modId);
        return name == null || name.isBlank() ? modId : name;
    }

    /**
     * Returns the description from the mod's metadata, shown on the Dungeon Configs page.
     * <pre>{@code
     * String about = CONFIG.description();
     * }</pre>
     *
     * @return the trimmed description, or an empty string
     */
    public String description() {
        String text = DcPlatform.modDescription(modId);
        return text == null ? "" : text.trim();
    }

    /**
     * Returns the installed version of the mod, from its metadata.
     * <pre>{@code
     * LOGGER.info("Loaded {} {}", CONFIG.displayName(), CONFIG.version());
     * }</pre>
     *
     * @return the version, or an empty string if unknown
     */
    public String version() {
        String version = DcPlatform.modVersion(modId);
        return version == null ? "" : version;
    }

    /**
     * Returns the mod's authors from its metadata, joined with commas.
     * <pre>{@code
     * String by = CONFIG.authors(); // e.g. "Alex, Sam"
     * }</pre>
     *
     * @return the authors, or an empty string if not available
     */
    public String authors() {
        String authors = DcPlatform.modAuthors(modId);
        return authors == null ? "" : authors;
    }

    /**
     * Returns the mod's license from its metadata.
     * <pre>{@code
     * String license = CONFIG.license(); // e.g. "MIT"
     * }</pre>
     *
     * @return the license, or an empty string if not available
     */
    public String license() {
        String license = DcPlatform.modLicense(modId);
        return license == null ? "" : license;
    }

    /**
     * Whether the settings screen shows the firefly effects. Turned off with {@link Builder#noFireflies()}.
     * <pre>{@code
     * boolean ambient = CONFIG.fireflies();
     * }</pre>
     *
     * @return {@code true} unless turned off
     */
    public boolean fireflies() {
        return fireflies;
    }

    /**
     * Returns the colour source given to {@link Builder#defaultColors(String)}.
     * <pre>{@code
     * String colors = CONFIG.defaultColors(); // "" when none was set
     * }</pre>
     *
     * @return the link, or an empty string
     */
    public String defaultColors() {
        return defaultColors;
    }

    /**
     * Returns the mod's config folder, {@code config/<modid>}, creating it if needed. The folder also
     * holds the screen's {@code bg}, {@code bgmusic} and {@code fonts} folders and {@code colors.json5}.
     * <pre>{@code
     * Path extra = CONFIG.directory().resolve("presets");
     * }</pre>
     *
     * @return the folder
     */
    public synchronized Path directory() {
        if (directory == null) {
            directory = DcPlatform.gameDirectory().resolve("config").resolve(modId);
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                DungeonConfigMod.LOGGER.error("[DungeonConfig] Couldn't create {}", directory, e);
            }
        }
        return directory;
    }

    /**
     * Returns the config file, {@code config/<modid>/<modid>.json5} unless changed with
     * {@link Builder#fileName(String)}.
     * <pre>{@code
     * boolean firstRun = !Files.exists(CONFIG.file());
     * }</pre>
     *
     * @return the file path
     */
    public Path file() {
        return directory().resolve(fileName);
    }

    /**
     * Whether any entry is marked with {@link ConfigEntry#synced()} or {@link ConfigEntry#syncedOr(Object)},
     * which means servers send this config to players.
     * <pre>{@code
     * if (CONFIG.hasSyncedEntries()) LOGGER.info("Some settings follow the server");
     * }</pre>
     *
     * @return {@code true} if at least one entry is synced
     */
    public boolean hasSyncedEntries() {
        for (ConfigEntry<?> entry : entries()) {
            if (entry.isSynced()) return true;
        }
        return false;
    }

    /**
     * Reads the config file, then writes it back so new entries and comments appear in it.
     * Creates the file with the defaults if it doesn't exist. Call it once, after adding every entry.
     * <p>
     * Values that can't be read keep their defaults. If the file has errors, the original is kept
     * next to it as {@code <file>.bad-<date>} before it is rewritten. Calling it again does the
     * same as {@link #reload()}.
     * <pre>{@code
     * public static void init() {
     *     MyModConfig.CONFIG.load();
     * }
     * }</pre>
     */
    public synchronized void load() {
        ensureAppearance();
        if (loaded) {
            reload();
            return;
        }
        loaded = true;
        Path path = file();
        try {
            if (Files.exists(path) && !loadFromFile(path)) {
                Path copy = FileUtil.backupBadFile(path);
                if (copy == null) {
                    DungeonConfigMod.LOGGER.error("[DungeonConfig] {} has errors and couldn't be backed up, leaving it untouched", path);
                    return;
                }
                DungeonConfigMod.LOGGER.warn("[DungeonConfig] {} has errors, the original was kept as {}. Settings that couldn't be read are back to defaults.",
                        path, copy.getFileName());
            }
            save();
        } catch (Exception e) {
            DungeonConfigMod.LOGGER.error("[DungeonConfig] Couldn't load {}", path, e);
        }
    }

    /**
     * Reads the config file again, for example after it was edited by hand. Does nothing if the
     * file doesn't exist, and never writes to it. Values that can't be read are left as they were.
     * <pre>{@code
     * // In a /mymod reload command
     * MyModConfig.CONFIG.reload();
     * MyModConfig.CONFIG.notifySaved();
     * }</pre>
     */
    public synchronized void reload() {
        Path path = file();
        if (Files.exists(path) && !loadFromFile(path)) {
            DungeonConfigMod.LOGGER.warn("[DungeonConfig] {} has errors, settings that couldn't be read were left as they were", path);
        }
    }

    /**
     * Whether {@link #load()} has been called.
     * <pre>{@code
     * if (!CONFIG.isLoaded()) CONFIG.load();
     * }</pre>
     *
     * @return {@code true} once loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    private boolean loadFromFile(Path path) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(Json5.stripTrailingCommas(Files.readString(path, StandardCharsets.UTF_8)));
            if (!parsed.isJsonObject()) throw new JsonParseException("the file is empty or not a JSON object");
            json = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            DungeonConfigMod.LOGGER.error("[DungeonConfig] Failed to read {}: {}", path, e.getMessage());
            return false;
        }
        boolean ok = true;
        for (ConfigEntry<?> entry : entries.values()) {
            if (!json.has(entry.getName())) continue;
            try {
                read(entry, json.get(entry.getName()));
            } catch (Exception e) {
                ok = false;
                DungeonConfigMod.LOGGER.warn("[DungeonConfig] Failed to load {} in {}: {}", entry.getName(), path.getFileName(), e.toString());
            }
        }
        return ok;
    }

    @SuppressWarnings("unchecked")
    private static void read(ConfigEntry<?> entry, JsonElement value) {
        Class<?> type = entry.getType();
        if (type == Boolean.class) {
            boolean b = value.getAsBoolean();
            if (entry.testValidator(b)) ((ConfigEntry<Boolean>) entry).setValue(b);
        } else if (type == Integer.class) {
            entry.setClamped(value.getAsInt());
        } else if (type == Long.class) {
            entry.setClamped(value.getAsLong());
        } else if (type == Double.class) {
            entry.setClamped(value.getAsDouble());
        } else if (entry.isEnum()) {
            if (value.isJsonPrimitive()) entry.readEnum(value.getAsString());
        } else if (entry.isList()) {
            if (value.isJsonArray()) entry.readList(value.getAsJsonArray());
        } else if (type == String.class) {
            String str = value.getAsString();
            if (entry.testValidator(str)) ((ConfigEntry<String>) entry).setValue(str);
        } else {
            try {
                Object obj = GSON.fromJson(value, type);
                if (obj != null && entry.testValidator(obj)) ((ConfigEntry<Object>) entry).setValue(obj);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Writes every entry's local value to the config file, with comments, replacing the old file in
     * one step so a crash can't leave it half written.
     * <p>
     * The settings screen calls this and then {@link #notifySaved()} when the player saves. Call both
     * after changing values from code, if the change should be kept.
     * <pre>{@code
     * MyModConfig.AURA_RADIUS.set(24);
     * if (MyModConfig.CONFIG.save()) MyModConfig.CONFIG.notifySaved();
     * }</pre>
     *
     * @return {@code true} if the file was written, {@code false} if writing failed (the error is logged)
     */
    public synchronized boolean save() {
        ensureAppearance();
        StringBuilder out = new StringBuilder();
        out.append("// ").append(displayName()).append(" configuration\n");
        out.append("// This file uses JSON5 syntax (https://json5.org/)\n");
        out.append("{\n");
        ConfigEntry<?>[] list = entries.values().toArray(new ConfigEntry[0]);
        for (int i = 0; i < list.length; i++) {
            ConfigEntry<?> entry = list[i];
            String comment = String.valueOf(entry.getComment()).replace("\r", "").replace("\n", "\n  // ");
            out.append("  // ").append(comment).append("\n");
            if (entry.isSynced()) out.append("  // Synced: players use the server's value while connected to it\n");
            out.append("  \"").append(entry.getName()).append("\": ");
            Object value = entry.getLocalValue();
            if (value instanceof Enum<?> e) {
                out.append(GSON.toJson(e.name()));
            } else if ((value instanceof Boolean || value instanceof Number) && entry.getType() != String.class) {
                out.append(value);
            } else {
                out.append(GSON.toJson(value));
            }
            out.append(i < list.length - 1 ? ",\n\n" : "\n");
        }
        out.append("}\n");

        Path path = file();
        try {
            FileUtil.writeStringAtomically(path, out);
            return true;
        } catch (IOException e) {
            DungeonConfigMod.LOGGER.error("[DungeonConfig] Failed to save {}", path, e);
            return false;
        }
    }

    /**
     * Registers code to run every time {@link #notifySaved()} is called, which is after the player
     * saves the settings screen. Use it to apply settings that are cached elsewhere. Exceptions
     * thrown by a listener are logged and don't stop the others.
     * <pre>{@code
     * MyModConfig.CONFIG.onSaved(() -> ParticleCache.rebuild(MyModConfig.AURA_RADIUS.get()));
     * }</pre>
     *
     * @param listener the code to run; it runs on the thread that called {@link #notifySaved()}
     */
    public void onSaved(Runnable listener) {
        saveListeners.add(listener);
    }

    /**
     * Runs the {@link #onSaved(Runnable) save listeners} and, on a server, sends the synced values
     * to every connected player again. The settings screen calls it after saving.
     * <pre>{@code
     * MyModConfig.CONFIG.reload();
     * MyModConfig.CONFIG.notifySaved();
     * }</pre>
     */
    public void notifySaved() {
        for (Runnable listener : saveListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                DungeonConfigMod.LOGGER.error("[DungeonConfig] A save listener of {} failed", modId, e);
            }
        }
        ConfigSync.resend(this);
    }

    /**
     * Collects the local values of the synced entries into the JSON that servers send to players.
     * Used by Dungeon Config's networking; mods rarely need it.
     * <pre>{@code
     * String payload = CONFIG.syncedValues().toString(); // {"betterDoorsEnabled":true}
     * }</pre>
     *
     * @return a new JSON object with one property per synced entry
     */
    public synchronized JsonObject syncedValues() {
        JsonObject json = new JsonObject();
        for (ConfigEntry<?> entry : entries.values()) {
            if (!entry.isSynced()) continue;
            Object value = entry.getLocalValue();
            if (value instanceof Boolean b) json.add(entry.getName(), new JsonPrimitive(b));
            else if (value instanceof Number n) json.add(entry.getName(), new JsonPrimitive(n));
            else if (value instanceof Enum<?> e) json.add(entry.getName(), new JsonPrimitive(e.name()));
            else if (value instanceof List<?> list) json.add(entry.getName(), GSON.toJsonTree(list));
            else if (value instanceof String s) json.add(entry.getName(), new JsonPrimitive(s));
            else if (value != null) json.add(entry.getName(), GSON.toJsonTree(value));
        }
        return json;
    }

    /**
     * Applies the values a server sent for the synced entries. While the player stays connected,
     * {@link ConfigEntry#get()} returns these instead of the local values. Used by Dungeon Config's
     * networking; mods rarely need it.
     * <pre>{@code
     * JsonObject fromServer = JsonParser.parseString(payload).getAsJsonObject();
     * CONFIG.applyServerValues(fromServer);
     * }</pre>
     *
     * @param json the values, as made by {@link #syncedValues()}
     */
    @SuppressWarnings("unchecked")
    public synchronized void applyServerValues(JsonObject json) {
        for (ConfigEntry<?> entry : entries.values()) {
            if (!entry.isSynced()) continue;
            JsonElement value = json.get(entry.getName());
            Object parsed = null;
            try {
                if (value != null) parsed = parseServerValue(entry, value);
            } catch (RuntimeException e) {
                DungeonConfigMod.LOGGER.warn("[DungeonConfig] Ignoring the server's value for {} in {}: {}", entry.getName(), modId, e.toString());
            }
            ((ConfigEntry<Object>) entry).setServerValue(parsed);
        }
    }

    // Turns a value sent by the server into the entry's type without touching the player's own value
    @Nullable
    private static Object parseServerValue(ConfigEntry<?> entry, JsonElement value) {
        Class<?> type = entry.getType();
        if (type == Boolean.class) return value.isJsonPrimitive() ? value.getAsBoolean() : null;
        if (type == Integer.class) return value.isJsonPrimitive() ? value.getAsInt() : null;
        if (type == Long.class) return value.isJsonPrimitive() ? value.getAsLong() : null;
        if (type == Double.class) return value.isJsonPrimitive() ? value.getAsDouble() : null;
        if (entry.isEnum()) return value.isJsonPrimitive() ? entry.parseEnum(value.getAsString()) : null;
        if (entry.isList()) return value.isJsonArray() ? entry.parseList(value.getAsJsonArray()) : null;
        if (type == String.class) return value.isJsonPrimitive() ? value.getAsString() : null;
        return GSON.fromJson(value, type);
    }

    /**
     * Forgets the values a server sent, so every entry goes back to its local value. Dungeon Config
     * calls it when the player leaves a server.
     * <pre>{@code
     * CONFIG.clearServerValues();
     * }</pre>
     */
    public synchronized void clearServerValues() {
        for (ConfigEntry<?> entry : entries.values()) entry.setServerValue(null);
    }

    @Override
    public String toString() {
        return "DungeonConfig[" + modId + "]";
    }
}
