package com.frikinjay.dungeonconfig.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A list of config entries defined with a builder shaped like NeoForge's {@code ModConfigSpec},
 * which makes moving an existing NeoForge config to Dungeon Config mostly a change of imports.
 * <p>
 * Pass the built spec to {@link DungeonConfig.Builder#spec(ModConfigSpec)} to add its entries.
 * <p>
 * Paths decide where each value appears. The first path segment is the tab, the last is the key in
 * the file, and anything in between becomes a section heading; a single segment goes into the
 * {@code "General"} tab. {@link Builder#push(String)} adds segments in front of every following path.
 * <pre>{@code
 * public final class MyModConfig {
 *     private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
 *
 *     public static final ModConfigSpec.BooleanValue SPELLS;
 *     public static final ModConfigSpec.IntValue MAX_MANA;
 *
 *     static {
 *         BUILDER.push("Gameplay");                       // tab "Gameplay"
 *         SPELLS = BUILDER.comment("Allow casting spells")
 *                 .define("spellsEnabled", true);           // key "spellsEnabled"
 *         MAX_MANA = BUILDER.comment("Largest mana pool")
 *                 .defineInRange("Mana.maxMana", 100, 10, 500); // section "Mana", key "maxMana"
 *         BUILDER.pop();
 *     }
 *
 *     public static final ModConfigSpec SPEC = BUILDER.build();
 *     public static final DungeonConfig CONFIG = DungeonConfig.builder("mymod").spec(SPEC).build();
 * }
 * }</pre>
 * Keys must be unique within the whole config, not just within a tab.
 */
public class ModConfigSpec {

    private final List<ConfigEntry<?>> entries;
    private final Map<String, ConfigEntry<?>> valuesByPath;
    @Nullable private DungeonConfig boundConfig;

    private ModConfigSpec(List<ConfigEntry<?>> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        Map<String, ConfigEntry<?>> map = new LinkedHashMap<>();
        for (ConfigEntry<?> entry : entries) {
            map.put(entry.getName(), entry);
        }
        this.valuesByPath = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the entries in the order they were defined.
     * <pre>{@code
     * int count = MyModConfig.SPEC.getEntries().size();
     * }</pre>
     *
     * @return the entries, which can't be modified
     */
    public List<ConfigEntry<?>> getEntries() {
        return entries;
    }

    /**
     * Finds an entry by its key, the last segment of the path it was defined with.
     * <pre>{@code
     * ConfigEntry<?> mana = MyModConfig.SPEC.getEntry("maxMana");
     * }</pre>
     *
     * @param path the entry's key
     * @return the entry, or {@code null} if there is none
     */
    @Nullable
    public ConfigEntry<?> getEntry(String path) {
        return valuesByPath.get(path);
    }

    /**
     * Whether the config this spec was given to has been {@link DungeonConfig#load() loaded}.
     * <pre>{@code
     * if (MyModConfig.SPEC.isLoaded()) applySettings();
     * }</pre>
     *
     * @return {@code false} before loading, or if the spec wasn't given to a config
     */
    public boolean isLoaded() {
        return boundConfig != null && boundConfig.isLoaded();
    }

    /**
     * Saves the config this spec was given to. Same as {@link DungeonConfig#save()}.
     * <pre>{@code
     * MyModConfig.MAX_MANA.set(200);
     * MyModConfig.SPEC.save();
     * }</pre>
     *
     * @return {@code true} if the file was written; {@code false} if writing failed or the spec isn't in a config
     */
    public boolean save() {
        return boundConfig != null && boundConfig.save();
    }

    void bind(DungeonConfig config) {
        this.boundConfig = config;
    }

    /**
     * A toggle, made with {@link Builder#define(String, boolean)}.
     */
    public static class BooleanValue extends ConfigEntry<Boolean> {
        public BooleanValue(String name, String comment, String category, boolean defaultValue) {
            super(name, comment, category, defaultValue, Boolean.class);
        }
    }

    /**
     * A whole number slider, made with {@link Builder#defineInRange(String, int, int, int)}.
     */
    public static class IntValue extends ConfigEntry<Integer> {
        public IntValue(String name, String comment, String category, int defaultValue, int min, int max) {
            super(name, comment, category, defaultValue, Integer.class, min, max);
        }

        /**
         * Returns the lowest allowed value.
         *
         * @return the lowest allowed value
         */
        public int getMin() {
            return getMinValue() != null ? getMinValue() : Integer.MIN_VALUE;
        }

        /**
         * Returns the highest allowed value.
         *
         * @return the highest allowed value
         */
        public int getMax() {
            return getMaxValue() != null ? getMaxValue() : Integer.MAX_VALUE;
        }
    }

    /**
     * A long slider, made with {@link Builder#defineInRange(String, long, long, long)}.
     */
    public static class LongValue extends ConfigEntry<Long> {
        public LongValue(String name, String comment, String category, long defaultValue, long min, long max) {
            super(name, comment, category, defaultValue, Long.class, min, max);
        }

        /**
         * Returns the lowest allowed value.
         *
         * @return the lowest allowed value
         */
        public long getMin() {
            return getMinValue() != null ? getMinValue() : Long.MIN_VALUE;
        }

        /**
         * Returns the highest allowed value.
         *
         * @return the highest allowed value
         */
        public long getMax() {
            return getMaxValue() != null ? getMaxValue() : Long.MAX_VALUE;
        }
    }

    /**
     * A decimal slider, made with {@link Builder#defineInRange(String, double, double, double)}.
     */
    public static class DoubleValue extends ConfigEntry<Double> {
        public DoubleValue(String name, String comment, String category, double defaultValue, double min, double max) {
            super(name, comment, category, defaultValue, Double.class, min, max);
        }

        /**
         * Returns the lowest allowed value.
         *
         * @return the lowest allowed value
         */
        public double getMin() {
            return getMinValue() != null ? getMinValue() : -Double.MAX_VALUE;
        }

        /**
         * Returns the highest allowed value.
         *
         * @return the highest allowed value
         */
        public double getMax() {
            return getMaxValue() != null ? getMaxValue() : Double.MAX_VALUE;
        }
    }

    /**
     * An enum dropdown, made with {@link Builder#defineEnum(String, Enum)}.
     *
     * @param <T> the enum type
     */
    public static class EnumValue<T extends Enum<T>> extends ConfigEntry<T> {
        public EnumValue(String name, String comment, String category, T defaultValue, Class<T> enumClass) {
            super(name, comment, category, defaultValue, enumClass);
        }
    }

    /**
     * Any other option, made with {@link Builder#define(String, Object)}, {@link Builder#defineInList}
     * or {@link Builder#defineList}.
     *
     * @param <T> the value type
     */
    public static class ConfigValue<T> extends ConfigEntry<T> {
        public ConfigValue(String name, String comment, String category, T defaultValue, Class<T> type) {
            super(name, comment, category, defaultValue, type);
        }

        public ConfigValue(String name, String comment, String category, T defaultValue, Class<T> type,
                           @Nullable T min, @Nullable T max) {
            super(name, comment, category, defaultValue, type, min, max);
        }
    }

    /**
     * Defines entries one after another. Comments, translations, {@link #worldRestart()} and
     * {@link #gameRestart()} apply only to the next entry defined; {@link #push(String) pushed} path segments apply until popped.
     * <pre>{@code
     * ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
     * builder.push("Client");
     * ModConfigSpec.BooleanValue HUD = builder.comment("Show the HUD").define("hudEnabled", true);
     * builder.pop();
     * ModConfigSpec spec = builder.build();
     * }</pre>
     */
    public static class Builder {

        private final List<ConfigEntry<?>> entries = new ArrayList<>();
        private final Deque<String> context = new ArrayDeque<>();
        private final StringBuilder commentBuilder = new StringBuilder();
        @Nullable private String translationKey;
        private RestartType restartType = RestartType.NONE;

        /**
         * Adds a path segment in front of every path defined until the matching {@link #pop()}.
         * The first segment is the tab; later ones become the section heading. Dots split a segment.
         * <pre>{@code
         * builder.push("Gameplay").push("Combat");
         * builder.define("pvpEnabled", true);   // tab "Gameplay", section "Combat"
         * builder.pop(2);
         * }</pre>
         *
         * @param path the segment
         * @return this builder
         */
        public Builder push(String path) {
            context.addLast(path);
            return this;
        }

        /**
         * Adds several path segments, as if {@link #push(String)} was called for each.
         * <pre>{@code
         * builder.push(List.of("Gameplay", "Combat"));
         * }</pre>
         *
         * @param path the segments
         * @return this builder
         */
        public Builder push(List<String> path) {
            for (String part : path) {
                push(part);
            }
            return this;
        }

        /**
         * Removes the last pushed segment. Does nothing if none is left.
         * <pre>{@code
         * builder.push("Gameplay");
         * builder.define("fastMode", false);
         * builder.pop();
         * }</pre>
         *
         * @return this builder
         */
        public Builder pop() {
            if (!context.isEmpty()) {
                context.removeLast();
            }
            return this;
        }

        /**
         * Removes the last {@code count} pushed segments.
         * <pre>{@code
         * builder.push("A").push("B");
         * builder.pop(2);
         * }</pre>
         *
         * @param count how many segments to remove
         * @return this builder
         */
        public Builder pop(int count) {
            for (int i = 0; i < count; i++) {
                pop();
            }
            return this;
        }

        /**
         * Sets the description of the next entry. Calling it more than once adds lines.
         * <pre>{@code
         * builder.comment("How far the aura reaches").defineInRange("auraRadius", 16, 1, 64);
         * }</pre>
         *
         * @param comment the description
         * @return this builder
         */
        public Builder comment(String comment) {
            if (commentBuilder.length() > 0) {
                commentBuilder.append("\n");
            }
            commentBuilder.append(comment);
            return this;
        }

        /**
         * Sets a description of several lines for the next entry.
         * <pre>{@code
         * builder.comment("How far the aura reaches", "Large values cost performance")
         *         .defineInRange("auraRadius", 16, 1, 64);
         * }</pre>
         *
         * @param comment the lines
         * @return this builder
         */
        public Builder comment(String... comment) {
            for (String line : comment) {
                comment(line);
            }
            return this;
        }

        /**
         * Sets the label of the next entry (see {@link ConfigEntry#withLabel(String)}). Unlike
         * NeoForge, the text is shown as is rather than looked up as a translation key; use
         * {@code <modid>.config.option.<key>} in a language file to translate an option.
         * <pre>{@code
         * builder.translation("Aura Radius").defineInRange("auraRadius", 16, 1, 64);
         * }</pre>
         *
         * @param translationKey the label
         * @return this builder
         */
        public Builder translation(String translationKey) {
            this.translationKey = translationKey;
            return this;
        }

        /**
         * Makes changes to the next entry wait until the world is left, as in NeoForge: {@link ConfigEntry#get()}
         * keeps returning the value it had when the world was joined (see {@link RestartType#WORLD}).
         * <pre>{@code
         * ModConfigSpec.IntValue ISLAND = builder.worldRestart().defineInRange("islandSize", 64, 16, 256);
         * }</pre>
         *
         * @return this builder
         */
        public Builder worldRestart() {
            this.restartType = this.restartType.with(RestartType.WORLD);
            return this;
        }

        /**
         * Makes changes to the next entry wait until the game is restarted, as in NeoForge:
         * {@link ConfigEntry#get()} keeps returning the first value it returned (see {@link RestartType#GAME}).
         * <pre>{@code
         * ModConfigSpec.BooleanValue SHADERS = builder.gameRestart().define("customShaders", true);
         * }</pre>
         *
         * @return this builder
         */
        public Builder gameRestart() {
            this.restartType = this.restartType.with(RestartType.GAME);
            return this;
        }

        private record ResolvedPath(String name, String category, @Nullable String section) {}

        private ResolvedPath resolve(String path) {
            List<String> parts = new ArrayList<>();
            for (String seg : context) {
                parts.addAll(List.of(seg.split("\\.")));
            }
            if (path != null && !path.isEmpty()) {
                parts.addAll(List.of(path.split("\\.")));
            }

            if (parts.isEmpty()) {
                return new ResolvedPath("unnamed", "General", null);
            }
            if (parts.size() == 1) {
                return new ResolvedPath(parts.get(0), "General", null);
            }
            if (parts.size() == 2) {
                return new ResolvedPath(parts.get(1), parts.get(0), null);
            }
            String category = parts.get(0);
            String name = parts.get(parts.size() - 1);
            String section = String.join(" - ", parts.subList(1, parts.size() - 1));
            return new ResolvedPath(name, category, section);
        }

        private String consumeComment() {
            String c = commentBuilder.toString();
            commentBuilder.setLength(0);
            return c;
        }

        // Applies the section from the path and the pending label/sync flags, then records the entry
        private <T extends ConfigEntry<?>> T configureEntry(T entry, ResolvedPath path) {
            if (path.section() != null) entry.withSection(path.section());
            if (translationKey != null) {
                entry.withLabel(translationKey);
                translationKey = null;
            }
            if (restartType != RestartType.NONE) {
                entry.withRestart(restartType);
                restartType = RestartType.NONE;
            }
            entries.add(entry);
            return entry;
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> classOf(T value) {
            return (Class<T>) (value != null ? value.getClass() : Object.class);
        }

        /**
         * Defines an option of any type, shown according to its type (see {@link ConfigEntry}).
         * <pre>{@code
         * ModConfigSpec.ConfigValue<String> GREETING = builder.define("Chat.greeting", "Welcome!");
         * }</pre>
         *
         * @param path         the key, optionally with tab and section segments in front
         * @param defaultValue the default; its class becomes the value type
         * @param <T>          the value type
         * @return the new entry
         */
        public <T> ConfigValue<T> define(String path, T defaultValue) {
            return define(path, defaultValue, null, classOf(defaultValue));
        }

        /**
         * Same as {@link #define(String, Object)} with the path given as segments.
         * <pre>{@code
         * builder.define(List.of("Chat", "greeting"), "Welcome!");
         * }</pre>
         */
        public <T> ConfigValue<T> define(List<String> path, T defaultValue) {
            return define(String.join(".", path), defaultValue);
        }

        /**
         * Defines an option whose values must pass a check (see {@link ConfigEntry#withValidator(Predicate)}).
         * <pre>{@code
         * builder.define("prefix", "!", v -> v instanceof String s && s.length() == 1);
         * }</pre>
         */
        public <T> ConfigValue<T> define(String path, T defaultValue, Predicate<Object> validator) {
            return define(path, defaultValue, validator, classOf(defaultValue));
        }

        /**
         * Defines an option with an explicit value class and an optional check.
         * <pre>{@code
         * builder.define("owner", "", null, String.class);
         * }</pre>
         */
        public <T> ConfigValue<T> define(String path, T defaultValue, @Nullable Predicate<Object> validator, Class<T> clazz) {
            ResolvedPath p = resolve(path);
            ConfigValue<T> entry = new ConfigValue<>(p.name(), consumeComment(), p.category(), defaultValue, clazz);
            if (validator != null) entry.withValidator(validator);
            return configureEntry(entry, p);
        }

        /**
         * Same as {@link #define(String, Object, Predicate, Class)} with the path given as segments.
         */
        public <T> ConfigValue<T> define(List<String> path, T defaultValue, @Nullable Predicate<Object> validator, Class<T> clazz) {
            return define(String.join(".", path), defaultValue, validator, clazz);
        }

        /**
         * Defines a toggle.
         * <pre>{@code
         * ModConfigSpec.BooleanValue SPELLS = builder.comment("Allow casting spells").define("spellsEnabled", true);
         * }</pre>
         *
         * @param path         the key, optionally with tab and section segments in front
         * @param defaultValue the default
         * @return the new entry
         */
        public BooleanValue define(String path, boolean defaultValue) {
            ResolvedPath p = resolve(path);
            return configureEntry(new BooleanValue(p.name(), consumeComment(), p.category(), defaultValue), p);
        }

        /**
         * Same as {@link #define(String, boolean)} with the path given as segments.
         */
        public BooleanValue define(List<String> path, boolean defaultValue) {
            return define(String.join(".", path), defaultValue);
        }

        /**
         * Defines a whole number slider. Values outside the range are clamped when read from the file.
         * <pre>{@code
         * ModConfigSpec.IntValue MAX_MANA = builder.defineInRange("maxMana", 100, 10, 500);
         * }</pre>
         *
         * @param path         the key, optionally with tab and section segments in front
         * @param defaultValue the default
         * @param min          the lowest value
         * @param max          the highest value
         * @return the new entry
         */
        public IntValue defineInRange(String path, int defaultValue, int min, int max) {
            ResolvedPath p = resolve(path);
            return configureEntry(new IntValue(p.name(), consumeComment(), p.category(), defaultValue, min, max), p);
        }

        /**
         * Same as {@link #defineInRange(String, int, int, int)} with the path given as segments.
         */
        public IntValue defineInRange(List<String> path, int defaultValue, int min, int max) {
            return defineInRange(String.join(".", path), defaultValue, min, max);
        }

        /**
         * Defines a long slider.
         * <pre>{@code
         * ModConfigSpec.LongValue COOLDOWN = builder.defineInRange("cooldownTicks", 200L, 0L, 72000L);
         * }</pre>
         *
         * @param path         the key, optionally with tab and section segments in front
         * @param defaultValue the default
         * @param min          the lowest value
         * @param max          the highest value
         * @return the new entry
         */
        public LongValue defineInRange(String path, long defaultValue, long min, long max) {
            ResolvedPath p = resolve(path);
            return configureEntry(new LongValue(p.name(), consumeComment(), p.category(), defaultValue, min, max), p);
        }

        /**
         * Same as {@link #defineInRange(String, long, long, long)} with the path given as segments.
         */
        public LongValue defineInRange(List<String> path, long defaultValue, long min, long max) {
            return defineInRange(String.join(".", path), defaultValue, min, max);
        }

        /**
         * Defines a decimal slider.
         * <pre>{@code
         * ModConfigSpec.DoubleValue CRIT = builder.defineInRange("critMultiplier", 1.5, 1.0, 5.0);
         * }</pre>
         *
         * @param path         the key, optionally with tab and section segments in front
         * @param defaultValue the default
         * @param min          the lowest value
         * @param max          the highest value
         * @return the new entry
         */
        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) {
            ResolvedPath p = resolve(path);
            return configureEntry(new DoubleValue(p.name(), consumeComment(), p.category(), defaultValue, min, max), p);
        }

        /**
         * Same as {@link #defineInRange(String, double, double, double)} with the path given as segments.
         */
        public DoubleValue defineInRange(List<String> path, double defaultValue, double min, double max) {
            return defineInRange(String.join(".", path), defaultValue, min, max);
        }

        /**
         * Defines a ranged option of another comparable type, such as {@code Float}.
         * <pre>{@code
         * builder.defineInRange("scale", 1.0F, 0.5F, 2.0F, Float.class);
         * }</pre>
         */
        public <T extends Comparable<? super T>> ConfigValue<T> defineInRange(String path, T defaultValue, T min, T max, Class<T> clazz) {
            ResolvedPath p = resolve(path);
            return configureEntry(new ConfigValue<>(p.name(), consumeComment(), p.category(), defaultValue, clazz, min, max), p);
        }

        /**
         * Same as {@link #defineInRange(String, Comparable, Comparable, Comparable, Class)} with the path given as segments.
         */
        public <T extends Comparable<? super T>> ConfigValue<T> defineInRange(List<String> path, T defaultValue, T min, T max, Class<T> clazz) {
            return defineInRange(String.join(".", path), defaultValue, min, max, clazz);
        }

        /**
         * Defines a dropdown limited to the given values. Other values are refused.
         * <pre>{@code
         * ModConfigSpec.ConfigValue<String> BIOME =
         *         builder.defineInList("startBiome", "plains", List.of("plains", "desert", "taiga"));
         * }</pre>
         *
         * @param path          the key, optionally with tab and section segments in front
         * @param defaultValue  the default
         * @param allowedValues the values in the dropdown
         * @param <T>           the value type
         * @return the new entry
         */
        public <T> ConfigValue<T> defineInList(String path, T defaultValue, Collection<T> allowedValues) {
            ResolvedPath p = resolve(path);
            ConfigValue<T> entry = new ConfigValue<>(p.name(), consumeComment(), p.category(), defaultValue, classOf(defaultValue));
            entry.withChoices(allowedValues);
            entry.withValidator(allowedValues::contains);
            return configureEntry(entry, p);
        }

        /**
         * Same as {@link #defineInList(String, Object, Collection)} with the path given as segments.
         */
        public <T> ConfigValue<T> defineInList(List<String> path, T defaultValue, Collection<T> allowedValues) {
            return defineInList(String.join(".", path), defaultValue, allowedValues);
        }

        /**
         * Defines a list that must not be empty; an empty list falls back to the default.
         * Items failing {@code elementValidator} are dropped. Items are converted to the type of the default
         * list's items (or of the supplier's), so a {@code List<Integer>} stays a list of integers.
         * <pre>{@code
         * ModConfigSpec.ConfigValue<List<String>> BLOCKED = builder.defineList("blockedItems",
         *         List.of("minecraft:tnt"), item -> item instanceof String s && s.contains(":"));
         * }</pre>
         *
         * @param path             the key, optionally with tab and section segments in front
         * @param defaultList      the default items
         * @param elementValidator check for each item
         * @param <T>              the item type
         * @return the new entry
         */
        public <T> ConfigValue<List<T>> defineList(String path, List<T> defaultList, Predicate<Object> elementValidator) {
            return defineList(path, defaultList, null, elementValidator, null, false);
        }

        /**
         * Same as {@link #defineList(String, List, Predicate)} with a supplier for new items
         * (see {@link ConfigEntry#withElementSupplier(Supplier)}).
         */
        public <T> ConfigValue<List<T>> defineList(String path, List<T> defaultList, @Nullable Supplier<T> defaultSupplier,
                                                  Predicate<Object> elementValidator) {
            return defineList(path, defaultList, defaultSupplier, elementValidator, null, false);
        }

        /**
         * Same as {@link #defineList(String, List, Predicate)} with the path given as segments.
         */
        public <T> ConfigValue<List<T>> defineList(List<String> path, List<T> defaultList, Predicate<Object> elementValidator) {
            return defineList(String.join(".", path), defaultList, elementValidator);
        }

        /**
         * Defines a list that may be empty.
         * <pre>{@code
         * ModConfigSpec.ConfigValue<List<String>> EXTRA = builder.defineListAllowEmpty("extraLoot",
         *         List.of(), item -> item instanceof String);
         * }</pre>
         */
        public <T> ConfigValue<List<T>> defineListAllowEmpty(String path, List<T> defaultList, Predicate<Object> elementValidator) {
            return defineList(path, defaultList, null, elementValidator, null, true);
        }

        /**
         * Same as {@link #defineListAllowEmpty(String, List, Predicate)} with a supplier for new items.
         */
        public <T> ConfigValue<List<T>> defineListAllowEmpty(String path, List<T> defaultList, @Nullable Supplier<T> defaultSupplier,
                                                            Predicate<Object> elementValidator) {
            return defineList(path, defaultList, defaultSupplier, elementValidator, null, true);
        }

        /**
         * Same as {@link #defineListAllowEmpty(String, List, Predicate)} with the path given as segments.
         */
        public <T> ConfigValue<List<T>> defineListAllowEmpty(List<String> path, List<T> defaultList, Predicate<Object> elementValidator) {
            return defineListAllowEmpty(String.join(".", path), defaultList, elementValidator);
        }

        /**
         * Defines a list with every option: a supplier for new items, a check per item, a check for the
         * whole list, and whether it may be empty.
         * <pre>{@code
         * builder.defineList("waypoints", List.of(), () -> "0,64,0",
         *         item -> item instanceof String, list -> list.size() <= 20, true);
         * }</pre>
         *
         * @param path             the key, optionally with tab and section segments in front
         * @param defaultList      the default items
         * @param defaultSupplier  makes new items, or {@code null}
         * @param elementValidator check for each item
         * @param sizeValidator    check for the whole list, or {@code null}
         * @param allowEmpty       whether the list may be empty
         * @param <T>              the item type
         * @return the new entry
         */
        @SuppressWarnings("unchecked")
        public <T> ConfigValue<List<T>> defineList(String path, List<T> defaultList, @Nullable Supplier<T> defaultSupplier,
                                                  Predicate<Object> elementValidator, @Nullable Predicate<List<?>> sizeValidator,
                                                  boolean allowEmpty) {
            ResolvedPath p = resolve(path);
            ConfigValue<List<T>> entry = new ConfigValue<>(p.name(), consumeComment(), p.category(), defaultList, (Class<List<T>>) (Class<?>) List.class);
            entry.withElementSupplier(defaultSupplier);
            entry.withElementValidator(elementValidator);
            entry.withSizeValidator(sizeValidator);
            entry.withListAllowEmpty(allowEmpty);
            return configureEntry(entry, p);
        }

        /**
         * Defines an enum dropdown listing every constant.
         * <pre>{@code
         * ModConfigSpec.EnumValue<Mode> MODE = builder.defineEnum("mode", Mode.NORMAL);
         * }</pre>
         *
         * @param path         the key, optionally with tab and section segments in front
         * @param defaultValue the default constant
         * @param <E>          the enum type
         * @return the new entry
         */
        public <E extends Enum<E>> EnumValue<E> defineEnum(String path, E defaultValue) {
            return defineEnum(path, defaultValue, defaultValue.getDeclaringClass());
        }

        /**
         * Defines an enum dropdown, naming the enum class explicitly.
         * <pre>{@code
         * builder.defineEnum("mode", Mode.NORMAL, Mode.class);
         * }</pre>
         */
        public <E extends Enum<E>> EnumValue<E> defineEnum(String path, E defaultValue, Class<E> enumClass) {
            ResolvedPath p = resolve(path);
            EnumValue<E> entry = new EnumValue<>(p.name(), consumeComment(), p.category(), defaultValue, enumClass);
            E[] constants = enumClass.getEnumConstants();
            if (constants != null) entry.withChoices(constants);
            return configureEntry(entry, p);
        }

        /**
         * Defines an enum dropdown limited to some of the constants. Other constants are refused.
         * <pre>{@code
         * builder.defineEnum("mode", Mode.NORMAL, List.of(Mode.EASY, Mode.NORMAL));
         * }</pre>
         */
        public <E extends Enum<E>> EnumValue<E> defineEnum(String path, E defaultValue, Collection<E> allowedValues) {
            ResolvedPath p = resolve(path);
            EnumValue<E> entry = new EnumValue<>(p.name(), consumeComment(), p.category(), defaultValue, defaultValue.getDeclaringClass());
            entry.withChoices(allowedValues);
            entry.withValidator(allowedValues::contains);
            return configureEntry(entry, p);
        }

        /**
         * Defines an enum dropdown limited to some constants, with a converter for old saved text
         * (see {@link ConfigEntry#withEnumConverter(Function)}).
         * <pre>{@code
         * builder.defineEnum("mode", Mode.NORMAL,
         *         raw -> "classic".equals(raw) ? Mode.EASY : null,
         *         List.of(Mode.EASY, Mode.NORMAL));
         * }</pre>
         */
        public <E extends Enum<E>> EnumValue<E> defineEnum(String path, E defaultValue, Function<Object, E> converter,
                                                          Collection<E> allowedValues) {
            ResolvedPath p = resolve(path);
            EnumValue<E> entry = new EnumValue<>(p.name(), consumeComment(), p.category(), defaultValue, defaultValue.getDeclaringClass());
            entry.withEnumConverter(converter);
            entry.withChoices(allowedValues);
            entry.withValidator(allowedValues::contains);
            return configureEntry(entry, p);
        }

        /**
         * Finishes the spec. Give it to {@link DungeonConfig.Builder#spec(ModConfigSpec)}.
         * <pre>{@code
         * ModConfigSpec spec = builder.build();
         * }</pre>
         *
         * @return the spec holding every defined entry
         */
        public ModConfigSpec build() {
            return new ModConfigSpec(entries);
        }
    }
}
