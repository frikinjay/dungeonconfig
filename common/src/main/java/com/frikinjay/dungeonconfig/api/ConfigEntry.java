package com.frikinjay.dungeonconfig.api;

import com.frikinjay.dungeonconfig.network.ConfigSync;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One option in a {@link DungeonConfig}: its key, description, tab, default and current value.
 * <p>
 * The value type decides how the option is shown on the settings screen:
 * <ul>
 *     <li>{@code Boolean}: a toggle</li>
 *     <li>enums, and any other type given {@link #withChoices(Object[]) choices}: a dropdown</li>
 *     <li>numbers with a min and max: a slider</li>
 *     <li>{@code List}: a text box of comma separated items</li>
 *     <li>everything else (text, numbers without a range, custom types as JSON): a text box, with a
 *     suggestion popup when {@link #withRegistry(Registry) a registry} is set</li>
 * </ul>
 * The {@code with*} methods return the entry itself, so they can be chained when the entry is created.
 * <pre>{@code
 * public static final ConfigEntry<Integer> DIFFICULTY = CONFIG.add(
 *         new ConfigEntry<>("difficultyStars", "How hard the boss is", "Gameplay", 2, Integer.class, 1, 5)
 *                 .withLabel("Boss Difficulty")
 *                 .withUnit(" stars"));
 *
 * int stars = DIFFICULTY.get();
 * }</pre>
 * <p>
 * The label shown on the screen is found in this order: the translation
 * {@code <modid>.config.option.<name>}, then {@link #withLabel(String)}, then the name split into words
 * ({@code "particlesEnabled"} becomes "Particles"). Descriptions use {@code <modid>.config.option.<name>.desc}
 * before the comment.
 *
 * @param <T> the value type
 */
public class ConfigEntry<T> implements ConfigValue<T> {

    private static final Gson GSON = new Gson();
    private static final Object HELD_EMPTY = new Object();

    private final String name;
    private final String comment;
    private final String category;
    private final T defaultValue;
    private volatile T value;
    private final Class<T> type;
    @Nullable private final T minValue;
    @Nullable private final T maxValue;

    @Nullable private String label;
    @Nullable private String section;
    @Nullable private List<T> choices;
    @Nullable private String unit;
    @Nullable private Double step;

    @Nullable private Predicate<Object> validator;
    @Nullable private Supplier<?> elementSupplier;
    @Nullable private Predicate<Object> elementValidator;
    @Nullable private Predicate<List<?>> sizeValidator;
    private boolean listAllowEmpty = true;
    @Nullable private Function<Object, T> enumConverter;

    @Nullable private Registry<?> registry;
    @Nullable private Collection<?> registrySubset;
    @Nullable private Predicate<?> registryFilter;
    @Nullable private Integer minEntries;
    @Nullable private Integer maxEntries;
    @Nullable private List<String> cachedRegistryIds;

    @Nullable private Class<?> elementType;

    private RestartType restartType = RestartType.NONE;
    // Value handed out by get() while a restart is needed for changes to apply; HELD_EMPTY when nothing is held
    private volatile Object heldValue = HELD_EMPTY;

    private boolean synced;
    private boolean hasFallback;
    @Nullable private T fallback;
    @Nullable private volatile T serverValue;
    @Nullable DungeonConfig owner;

    /**
     * Creates an option without a range, such as a toggle, text, enum or list.
     * <pre>{@code
     * new ConfigEntry<>("greeting", "Message shown on join", "Chat", "Welcome!", String.class);
     * new ConfigEntry<>("mode", "Which mode to use", "General", Mode.FAST, Mode.class);
     * }</pre>
     *
     * @param name         the key in the config file; must be unique within the config
     * @param comment      the description, shown on the screen and written above the value in the file
     * @param category     the tab it appears in; {@code null} means {@code "General"}
     * @param defaultValue the starting value, also used by {@link #resetToDefault()}
     * @param type         the value class; {@code null} means the class of {@code defaultValue}
     */
    public ConfigEntry(String name, String comment, String category, T defaultValue, Class<T> type) {
        this(name, comment, category, defaultValue, type, null, null);
    }

    /**
     * Creates an option with a range. Numbers with both bounds are shown as sliders, and values
     * read from the file are clamped into the range.
     * <pre>{@code
     * new ConfigEntry<>("rangeBlocks", "Search range", "Gameplay", 8, Integer.class, 1, 32);
     * new ConfigEntry<>("speed", "Movement multiplier", "Gameplay", 1.0, Double.class, 0.5, 2.0);
     * }</pre>
     *
     * @param name         the key in the config file
     * @param comment      the description
     * @param category     the tab it appears in
     * @param defaultValue the starting value
     * @param type         the value class
     * @param minValue     the lowest value, or {@code null} for no range
     * @param maxValue     the highest value, or {@code null} for no range
     */
    @SuppressWarnings("unchecked")
    public ConfigEntry(String name, String comment, String category, T defaultValue, Class<T> type,
                       @Nullable T minValue, @Nullable T maxValue) {
        this.name = Objects.requireNonNull(name, "Config entry name cannot be null");
        this.comment = comment != null ? comment : "";
        this.category = category != null ? category : "General";
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        Class<T> resolved = type != null ? type : (defaultValue != null ? (Class<T>) defaultValue.getClass() : (Class<T>) Object.class);
        // Enum constants with a body are instances of an anonymous subclass; use the enum itself
        if (!resolved.isEnum() && defaultValue instanceof Enum<?> e) resolved = (Class<T>) e.getDeclaringClass();
        this.type = resolved;
        this.minValue = minValue;
        this.maxValue = maxValue;

        if (isEnum()) {
            T[] constants = this.type.getEnumConstants();
            if (constants != null) this.choices = List.of(constants);
        }
    }

    /**
     * Sets the label shown on the screen when the mod has no translation for this option.
     * <pre>{@code
     * new ConfigEntry<>("tpCmdHome", "Enable /home", "Commands", true, Boolean.class).withLabel("/home");
     * }</pre>
     *
     * @param label the label
     * @return this entry
     */
    public ConfigEntry<T> withLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Groups the option under a heading inside its tab. Consecutive options with the same section
     * share one heading. The heading can be translated with {@code <modid>.config.section.<section>}
     * (lower case, spaces as underscores).
     * <pre>{@code
     * CONFIG.addInt("rtpRange", "Teleports", 1000, 100, 100000, "How far /rtp can go").withSection("Random Teleport");
     * }</pre>
     *
     * @param section the heading
     * @return this entry
     */
    public ConfigEntry<T> withSection(String section) {
        this.section = section;
        return this;
    }

    /**
     * Shows the option as a dropdown limited to these values. The order is kept.
     * <pre>{@code
     * new ConfigEntry<>("fps", "Animation frame rate", "Visuals", 30, Integer.class, 30, 60).withChoices(30, 60);
     * }</pre>
     *
     * @param values the allowed values
     * @return this entry
     */
    @SafeVarargs
    public final ConfigEntry<T> withChoices(T... values) {
        this.choices = List.of(values);
        return this;
    }

    /**
     * Shows the option as a dropdown limited to these values.
     * <pre>{@code
     * new ConfigEntry<>("biome", "Starting biome", "World", "plains", String.class)
     *         .withChoices(List.of("plains", "desert", "taiga"));
     * }</pre>
     *
     * @param values the allowed values
     * @return this entry
     */
    public ConfigEntry<T> withChoices(Collection<T> values) {
        this.choices = new ArrayList<>(values);
        return this;
    }

    /**
     * Sets text appended to the shown value, such as {@code "%"}, {@code " blocks"} or {@code "x"}.
     * Without it, names ending in {@code Percent}, {@code Seconds}, {@code Minutes} and {@code Fps}
     * get a matching unit automatically.
     * <pre>{@code
     * CONFIG.addDouble("zoomLevel", "Zoom", 3.0, 1.0, 50.0, "Starting zoom").withUnit("x");
     * }</pre>
     *
     * @param unit the unit text
     * @return this entry
     */
    public ConfigEntry<T> withUnit(String unit) {
        this.unit = unit;
        return this;
    }

    /**
     * Sets how much the slider and the arrow keys change the value. Defaults to 1 for whole numbers
     * and 0.1 for decimals.
     * <pre>{@code
     * CONFIG.addInt("volumePercent", "Audio", 50, 0, 100, "Volume").withStep(5);
     * }</pre>
     *
     * @param step the step, greater than zero
     * @return this entry
     * @throws IllegalArgumentException if {@code step} isn't positive
     */
    public ConfigEntry<T> withStep(double step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Config entry " + name + ": step must be greater than zero");
        }
        this.step = step;
        return this;
    }

    /**
     * Adds a check every new value must pass, whether it comes from the file, the screen or
     * {@link #set(Object)}. Values that fail are ignored and the old value is kept.
     * <pre>{@code
     * CONFIG.addString("prefix", "Chat", "!", "Command prefix")
     *         .withValidator(v -> v instanceof String s && s.length() == 1);
     * }</pre>
     *
     * @param validator returns {@code true} for acceptable values
     * @return this entry
     */
    public ConfigEntry<T> withValidator(Predicate<Object> validator) {
        this.validator = validator;
        return this;
    }

    /**
     * Sets where new list items come from. Stored for screens and specs that add items; Dungeon
     * Config's own screen edits lists as text and doesn't call it.
     * <pre>{@code
     * listEntry.withElementSupplier(() -> "minecraft:stone");
     * }</pre>
     *
     * @param supplier makes a new item
     * @return this entry
     */
    public ConfigEntry<T> withElementSupplier(Supplier<?> supplier) {
        this.elementSupplier = supplier;
        return this;
    }

    /**
     * Adds a check for each item of a list option. Items that fail are dropped when the list is read
     * from the file or typed in, while the rest are kept.
     * <pre>{@code
     * listEntry.withElementValidator(item -> item instanceof String s && s.contains(":"));
     * }</pre>
     *
     * @param elementValidator returns {@code true} for acceptable items
     * @return this entry
     */
    public ConfigEntry<T> withElementValidator(Predicate<Object> elementValidator) {
        this.elementValidator = elementValidator;
        return this;
    }

    /**
     * Adds a check for a whole list option. Lists that fail are ignored and the old value is kept.
     * <pre>{@code
     * listEntry.withSizeValidator(list -> list.size() <= 10);
     * }</pre>
     *
     * @param sizeValidator returns {@code true} for acceptable lists
     * @return this entry
     */
    public ConfigEntry<T> withSizeValidator(Predicate<List<?>> sizeValidator) {
        this.sizeValidator = sizeValidator;
        return this;
    }

    /**
     * Sets whether a list option may be empty. When it may not, an empty list read from the file or
     * typed in is replaced by the default list. Lists may be empty unless this is set to {@code false}.
     * <pre>{@code
     * listEntry.withListAllowEmpty(false);
     * }</pre>
     *
     * @param allowEmpty {@code true} to accept empty lists
     * @return this entry
     */
    public ConfigEntry<T> withListAllowEmpty(boolean allowEmpty) {
        this.listAllowEmpty = allowEmpty;
        return this;
    }

    /**
     * Sets how saved text is turned into an enum value, for files written by older versions of a mod.
     * Tried before the constant names; if it returns {@code null} the names and ordinals are tried.
     * <pre>{@code
     * modeEntry.withEnumConverter(raw -> "legacy".equals(raw) ? Mode.CLASSIC : null);
     * }</pre>
     *
     * @param converter turns the saved text into a value, or returns {@code null}
     * @return this entry
     */
    public ConfigEntry<T> withEnumConverter(Function<Object, T> converter) {
        this.enumConverter = converter;
        return this;
    }

    /**
     * Shows a searchable popup of the registry's ids while the option's text box is being edited.
     * Works for text and list options; picking an id fills in the text or the list item under the caret.
     * <pre>{@code
     * public static final ConfigEntry<String> ICON = CONFIG.add(
     *         new ConfigEntry<>("iconItem", "Item shown as the icon", "General", "minecraft:diamond", String.class)
     *                 .withRegistry(BuiltInRegistries.ITEM));
     * }</pre>
     *
     * @param registry the registry to suggest ids from
     * @return this entry
     */
    public ConfigEntry<T> withRegistry(Registry<?> registry) {
        this.registry = registry;
        this.cachedRegistryIds = null;
        return this;
    }

    /**
     * Like {@link #withRegistry(Registry)}, but only suggests the given objects or ids.
     * <pre>{@code
     * hoeEntry.withRegistry(BuiltInRegistries.ITEM, List.of(Items.IRON_HOE, Items.DIAMOND_HOE, "minecraft:netherite_hoe"));
     * }</pre>
     *
     * @param registry       the registry the objects belong to
     * @param allowedObjects registry objects, or ids as strings
     * @return this entry
     */
    public ConfigEntry<T> withRegistry(Registry<?> registry, Collection<?> allowedObjects) {
        this.registry = registry;
        this.registrySubset = allowedObjects != null ? new ArrayList<>(allowedObjects) : null;
        this.cachedRegistryIds = null;
        return this;
    }

    /**
     * Narrows the registry suggestions. The filter is offered each registry key, then the key as a
     * string, then the registered object; an id is suggested if any of them passes.
     * Has no effect when {@link #withRegistry(Registry, Collection) a subset} is given.
     * <pre>{@code
     * itemEntry.withRegistry(BuiltInRegistries.ITEM)
     *         .withRegistryFilter(key -> key.toString().endsWith("_log"));
     * }</pre>
     *
     * @param filter returns {@code true} for ids to suggest
     * @return this entry
     */
    public ConfigEntry<T> withRegistryFilter(Predicate<?> filter) {
        this.registryFilter = filter;
        this.cachedRegistryIds = null;
        return this;
    }

    /**
     * Requires at least this many items in a list option. For a text option, 1 means it can't be blank.
     * Values with fewer items are ignored.
     * <pre>{@code
     * listEntry.withMinEntries(1);
     * }</pre>
     *
     * @param min the minimum, negative values count as 0
     * @return this entry
     */
    public ConfigEntry<T> withMinEntries(int min) {
        this.minEntries = Math.max(0, min);
        return this;
    }

    /**
     * Allows at most this many items in a list option; extra typed items are cut off. For a text
     * option, 0 means it must stay blank.
     * <pre>{@code
     * listEntry.withMaxEntries(5);
     * }</pre>
     *
     * @param max the maximum, negative values count as 0
     * @return this entry
     */
    public ConfigEntry<T> withMaxEntries(int max) {
        this.maxEntries = Math.max(0, max);
        return this;
    }

    /**
     * Sets the class of a list option's items. Items read from the file or typed on the screen are
     * converted to it, and items that can't be converted are dropped. Without it the class is taken
     * from the first item of the default list, or from the {@link #withElementSupplier(Supplier) supplier}.
     * <pre>{@code
     * new ConfigEntry<List<Integer>>("levels", "Levels to unlock", "General", new ArrayList<>(), (Class) List.class)
     *         .withElementType(Integer.class);
     * }</pre>
     *
     * @param elementType the item class, such as {@code Integer.class} or {@code String.class}
     * @return this entry
     */
    public ConfigEntry<T> withElementType(Class<?> elementType) {
        this.elementType = elementType;
        return this;
    }

    /**
     * Makes saved changes wait for a world or game restart before {@link #get()} returns them, like
     * NeoForge's {@code worldRestart()} and {@code gameRestart()}. The screen marks the option and,
     * after saving, tells the player when a restart is needed.
     * <pre>{@code
     * CONFIG.addInt("islandSize", "World", 64, 16, 256, "Size of the starting island").withRestart(RestartType.WORLD);
     * }</pre>
     *
     * @param restartType when changes take effect
     * @return this entry
     */
    public ConfigEntry<T> withRestart(RestartType restartType) {
        this.restartType = Objects.requireNonNull(restartType);
        this.heldValue = HELD_EMPTY;
        return this;
    }

    /**
     * Returns when saved changes to this option take effect.
     * <pre>{@code
     * if (ISLAND_SIZE.getRestartType() == RestartType.WORLD) LOGGER.info("Rejoin to apply");
     * }</pre>
     *
     * @return the restart requirement, {@link RestartType#NONE} unless set
     */
    public RestartType getRestartType() {
        return restartType;
    }

    /**
     * Whether the player's value differs from the one {@link #get()} is still returning because the
     * world or game hasn't been restarted yet.
     * <pre>{@code
     * if (ISLAND_SIZE.isRestartPending()) showHint("Rejoin the world to apply the new island size");
     * }</pre>
     *
     * @return {@code true} while a saved change is waiting for a restart
     */
    public boolean isRestartPending() {
        Object held = heldValue;
        return held != HELD_EMPTY && !Objects.equals(held, value);
    }

    // Lets values held for a world restart go, so the next get() picks up the saved value
    void releaseHeldValue(RestartType type) {
        if (restartType == type) heldValue = HELD_EMPTY;
    }

    /**
     * Makes servers send their value of this option to players. While a player is on a server that
     * has the mod, {@link #get()} returns the server's value, and the screen shows the option as
     * controlled by the server. On a server without the mod the player's own value is used.
     * <pre>{@code
     * CONFIG.addBoolean("pvpEnabled", "Gameplay", true, "Players can hurt each other").synced();
     * }</pre>
     *
     * @return this entry
     */
    public ConfigEntry<T> synced() {
        this.synced = true;
        return this;
    }

    /**
     * Like {@link #synced()}, but uses {@code valueWithoutServer} on servers that don't send a value,
     * such as servers without the mod. Use it for options that need server support to work.
     * <pre>{@code
     * // Walking through open doors needs the server to agree, so it is off on servers without the mod
     * CONFIG.addBoolean("betterDoorsEnabled", "Gameplay", true, "Open doors have no collision").syncedOr(false);
     * }</pre>
     *
     * @param valueWithoutServer the value used on servers that send none
     * @return this entry
     */
    public ConfigEntry<T> syncedOr(T valueWithoutServer) {
        this.synced = true;
        this.hasFallback = true;
        this.fallback = valueWithoutServer;
        return this;
    }

    /**
     * Same as {@link #getValue()}.
     */
    @Override
    public T get() {
        return getValue();
    }

    /**
     * Same as {@link #setValue(Object)}.
     */
    @Override
    public void set(T value) {
        setValue(value);
    }

    /**
     * Same as {@link #getDefaultValue()}.
     */
    @Override
    public T getDefault() {
        return defaultValue;
    }

    /**
     * Same as {@link #getName()}.
     */
    @Override
    public String getPath() {
        return name;
    }

    /**
     * Returns the value to use right now. On a server that syncs this option, that is the server's
     * value; otherwise the player's own.
     * <pre>{@code
     * if (PVP.getValue()) allowDamage();
     * }</pre>
     *
     * @return the value in effect
     */
    @SuppressWarnings("unchecked")
    public T getValue() {
        if (synced && ConfigSync.onRemoteServer()) {
            T fromServer = serverValue;
            if (fromServer != null) return fromServer;
            if (hasFallback) return fallback;
        }
        if (restartType == RestartType.NONE || (restartType == RestartType.WORLD && !ConfigSync.inWorld())) return value;
        Object held = heldValue;
        if (held == HELD_EMPTY) {
            held = value;
            heldValue = held;
        }
        return (T) held;
    }

    /**
     * Returns the player's own value, ignoring any server. This is what the screen edits and the
     * file stores.
     * <pre>{@code
     * boolean ownChoice = PVP.getLocalValue();
     * }</pre>
     *
     * @return the local value
     */
    public T getLocalValue() {
        return value;
    }

    /**
     * Changes the local value, if it passes the {@link #testValidator(Object) checks}. The change is
     * only written to the file by {@link DungeonConfig#save()}. Numbers are not clamped here; use
     * {@link #setClamped(Number)} for that.
     * <pre>{@code
     * RADIUS.setValue(24);
     * CONFIG.save();
     * }</pre>
     *
     * @param value the new value
     */
    public void setValue(T value) {
        if (testValidator(value)) {
            this.value = value;
        }
    }

    /**
     * Puts back a local value read earlier with {@link #getLocalValue()}, without the validator checks,
     * for undoing edits.
     * <pre>{@code
     * Integer before = RADIUS.getLocalValue();
     * RADIUS.set(10);
     * RADIUS.revertTo(before);
     * }</pre>
     *
     * @param value the earlier value
     */
    public void revertTo(T value) {
        this.value = value;
    }

    /**
     * Puts the local value back to the default. Validators are not checked.
     * <pre>{@code
     * RADIUS.resetToDefault();
     * }</pre>
     */
    @Override
    public void resetToDefault() {
        this.value = defaultValue;
    }

    /**
     * Whether the local value equals the default. The screen marks options that aren't.
     * <pre>{@code
     * if (!RADIUS.isDefault()) LOGGER.info("Custom radius: {}", RADIUS.get());
     * }</pre>
     *
     * @return {@code true} if unchanged
     */
    @Override
    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    /**
     * Converts a number to this option's type, clamps it into the range if there is one, and sets it.
     * Does nothing for options that aren't {@code Integer}, {@code Long}, {@code Double} or {@code Float}.
     * <pre>{@code
     * RADIUS.setClamped(500);   // range 1..64, so the value becomes 64
     * RADIUS.setClamped(12.7);  // becomes 12
     * }</pre>
     *
     * @param number the new value
     */
    @SuppressWarnings("unchecked")
    public void setClamped(Number number) {
        if (type == Integer.class) {
            int v = number.intValue();
            if (hasRange()) v = Math.max(((Number) minValue).intValue(), Math.min(((Number) maxValue).intValue(), v));
            setValue((T) Integer.valueOf(v));
        } else if (type == Long.class) {
            long v = number.longValue();
            if (hasRange()) v = Math.max(((Number) minValue).longValue(), Math.min(((Number) maxValue).longValue(), v));
            setValue((T) Long.valueOf(v));
        } else if (type == Double.class) {
            double v = number.doubleValue();
            if (hasRange()) v = Math.max(((Number) minValue).doubleValue(), Math.min(((Number) maxValue).doubleValue(), v));
            setValue((T) Double.valueOf(v));
        } else if (type == Float.class) {
            float v = number.floatValue();
            if (hasRange()) v = Math.max(((Number) minValue).floatValue(), Math.min(((Number) maxValue).floatValue(), v));
            setValue((T) Float.valueOf(v));
        }
    }

    /**
     * Sets an enum option from text: through the {@link #withEnumConverter(Function) converter} first,
     * then by constant name (any case) or ordinal among the choices. Unknown text is ignored.
     * <pre>{@code
     * MODE.readEnum("fast"); // Mode.FAST
     * MODE.readEnum("1");    // the second choice
     * }</pre>
     *
     * @param str the saved text; {@code null} is ignored
     */
    public void readEnum(String str) {
        T parsed = parseEnum(str);
        if (parsed != null) setValue(parsed);
    }

    /**
     * Turns text into one of this option's enum values the way {@link #readEnum(String)} does, without
     * changing the option.
     * <pre>{@code
     * Mode mode = MODE.parseEnum("fast");
     * }</pre>
     *
     * @param str the text
     * @return the value, or {@code null} if the text doesn't name an acceptable one
     */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public T parseEnum(@Nullable String str) {
        if (str == null) return null;
        if (enumConverter != null) {
            T converted = enumConverter.apply(str);
            if (converted != null && testValidator(converted)) return converted;
        }
        for (T choice : getChoices()) {
            if (choice instanceof Enum<?> e && (e.name().equalsIgnoreCase(str) || String.valueOf(e.ordinal()).equals(str))
                    && testValidator(choice)) {
                return choice;
            }
        }
        if (type.isEnum()) {
            try {
                T e = (T) Enum.valueOf((Class<Enum>) type, str.toUpperCase(Locale.ROOT));
                if (testValidator(e)) return e;
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    /**
     * Sets a list option from a JSON array. Items are converted to the {@link #withElementType(Class)
     * item type}; items that can't be converted or fail the element check are dropped.
     * <pre>{@code
     * TAGS.readList(JsonParser.parseString("[\"a\", \"b\"]").getAsJsonArray());
     * }</pre>
     *
     * @param array the saved items
     */
    public void readList(JsonArray array) {
        T parsed = parseList(array);
        if (parsed != null) setValue(parsed);
    }

    /**
     * Turns a JSON array into a value for this list option the way {@link #readList(JsonArray)} does,
     * without changing the option.
     * <pre>{@code
     * List<String> items = TAGS.parseList(array);
     * }</pre>
     *
     * @param array the items
     * @return the list, or {@code null} if it isn't acceptable
     */
    @Nullable
    public T parseList(JsonArray array) {
        List<Object> list = new ArrayList<>();
        for (JsonElement el : array) {
            Object item = convertElement(el);
            if (item != null && acceptsElement(item)) list.add(item);
        }
        return checkedList(list, false);
    }

    /**
     * Sets a list option from text such as {@code "a, b, c"}, as typed on the settings screen. Items
     * are trimmed, converted to the {@link #withElementType(Class) item type}, blank ones skipped, and
     * items past {@link #withMaxEntries(int) the maximum} cut off. Does nothing for options that aren't lists.
     * <pre>{@code
     * TAGS.setListFromCommaSeparated("minecraft:logs, minecraft:planks");
     * }</pre>
     *
     * @param text comma separated items
     */
    public void setListFromCommaSeparated(String text) {
        if (!isList()) return;
        List<Object> list = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            Object item = convertElement(new JsonPrimitive(trimmed));
            if (item != null && acceptsElement(item)) list.add(item);
        }
        T checked = checkedList(list, true);
        if (checked != null) setValue(checked);
    }

    private boolean acceptsElement(Object item) {
        return elementValidator == null || elementValidator.test(item);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private T checkedList(List<Object> list, boolean capAtMax) {
        if (list.isEmpty() && !listAllowEmpty && defaultValue instanceof List<?> def) {
            list = new ArrayList<>(def);
        }
        if (capAtMax && maxEntries != null && list.size() > maxEntries) {
            list = new ArrayList<>(list.subList(0, maxEntries));
        }
        return (sizeValidator == null || sizeValidator.test(list)) && testValidator(list) ? (T) list : null;
    }

    @Nullable
    private Class<?> elementType() {
        if (elementType != null) return elementType;
        if (defaultValue instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) return item.getClass();
            }
        }
        if (elementSupplier != null) {
            try {
                Object item = elementSupplier.get();
                if (item != null) return item.getClass();
            } catch (RuntimeException ignored) {}
        }
        return null;
    }

    // Converts a saved or typed list item to the item type; null when it can't be converted
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertElement(JsonElement el) {
        Class<?> target = elementType();
        try {
            if (el.isJsonPrimitive()) {
                JsonPrimitive p = el.getAsJsonPrimitive();
                if (target == null) {
                    if (p.isBoolean()) return p.getAsBoolean();
                    if (p.isNumber()) return wholeOrDecimal(p.getAsNumber());
                    return p.getAsString();
                }
                String text = p.getAsString().trim();
                if (target == String.class) return p.getAsString();
                if (target == Integer.class) return p.isNumber() ? p.getAsInt() : Integer.parseInt(text);
                if (target == Long.class) return p.isNumber() ? p.getAsLong() : Long.parseLong(text);
                if (target == Double.class) return p.isNumber() ? p.getAsDouble() : Double.parseDouble(text);
                if (target == Float.class) return p.isNumber() ? p.getAsFloat() : Float.parseFloat(text);
                if (target == Short.class) return p.isNumber() ? p.getAsShort() : Short.parseShort(text);
                if (target == Byte.class) return p.isNumber() ? p.getAsByte() : Byte.parseByte(text);
                if (target == Boolean.class) {
                    if (p.isBoolean()) return p.getAsBoolean();
                    return text.equalsIgnoreCase("true") ? Boolean.TRUE : text.equalsIgnoreCase("false") ? Boolean.FALSE : null;
                }
                if (target.isEnum()) {
                    for (Object constant : target.getEnumConstants()) {
                        if (((Enum) constant).name().equalsIgnoreCase(text)) return constant;
                    }
                    return null;
                }
            }
            if (target == null || target == String.class) return el.isJsonPrimitive() ? el.getAsString() : el.toString();
            return GSON.fromJson(el, target);
        } catch (RuntimeException notConvertible) {
            return null;
        }
    }

    // Gson reads untyped numbers as LazilyParsedNumber; turn them into Integer, Long or Double
    private static Number wholeOrDecimal(Number n) {
        double d = n.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            long l = n.longValue();
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
            return l;
        }
        return d;
    }

    /**
     * Returns the local value as the text the settings screen edits: comma separated items for lists,
     * JSON for custom types, plain text otherwise.
     * <pre>{@code
     * String shown = TAGS.toEditText(); // "minecraft:logs, minecraft:planks"
     * }</pre>
     *
     * @return the editable text
     */
    public String toEditText() {
        Object v = value;
        if (v instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) parts.add(String.valueOf(item));
            return String.join(", ", parts);
        }
        if (v == null) return "";
        if (v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Enum<?>) return String.valueOf(v);
        return GSON.toJson(v);
    }

    /**
     * Sets the local value from text typed on the settings screen: numbers are parsed and clamped,
     * lists split at commas, custom types read as JSON. Text that can't be read is ignored.
     * <pre>{@code
     * boolean changed = SEED.setFromEditText("12345");
     * }</pre>
     *
     * @param text the typed text
     * @return {@code true} if the value was accepted
     */
    @SuppressWarnings("unchecked")
    public boolean setFromEditText(String text) {
        T before = value;
        try {
            if (isList()) {
                setListFromCommaSeparated(text);
            } else if (type == String.class) {
                setValue((T) text);
            } else if (isEnum()) {
                readEnum(text.trim());
            } else if (type == Boolean.class) {
                String t = text.trim();
                if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) setValue((T) Boolean.valueOf(t));
            } else if (type == Integer.class) {
                setClamped(Integer.parseInt(text.trim()));
            } else if (type == Long.class) {
                setClamped(Long.parseLong(text.trim()));
            } else if (type == Float.class) {
                setClamped(Float.parseFloat(text.trim()));
            } else if (type == Double.class) {
                setClamped(Double.parseDouble(text.trim()));
            } else {
                T parsed = GSON.fromJson(text, type);
                if (parsed != null) setValue(parsed);
            }
        } catch (RuntimeException unreadable) {
            return false;
        }
        return !Objects.equals(before, value);
    }

    /**
     * Whether a value passes the {@link #withMinEntries(int) entry count} limits and the
     * {@link #withValidator(Predicate) validator}.
     * <pre>{@code
     * if (PREFIX.testValidator("!!")) PREFIX.set("!!");
     * }</pre>
     *
     * @param val the value to check
     * @return {@code true} if it would be accepted
     */
    public boolean testValidator(Object val) {
        if (minEntries != null || maxEntries != null) {
            int count = -1;
            if (val instanceof List<?> l) count = l.size();
            else if (val instanceof String s) count = s.trim().isEmpty() ? 0 : 1;
            if (count >= 0) {
                if (minEntries != null && count < minEntries) return false;
                if (maxEntries != null && count > maxEntries) return false;
            }
        }
        return validator == null || validator.test(val);
    }

    /**
     * Whether this option follows the server's value, set with {@link #synced()} or {@link #syncedOr(Object)}.
     * <pre>{@code
     * boolean followsServer = PVP.isSynced();
     * }</pre>
     *
     * @return {@code true} if synced
     */
    public boolean isSynced() {
        return synced;
    }

    /**
     * Returns the value the server sent, which is kept until the player leaves.
     * <pre>{@code
     * Boolean fromServer = PVP.getServerValue();
     * }</pre>
     *
     * @return the server's value, or {@code null} if none was received
     */
    @Nullable
    public T getServerValue() {
        return serverValue;
    }

    /**
     * Whether {@link #get()} currently returns a value decided by the server instead of the player's own.
     * <pre>{@code
     * if (PVP.isServerControlled()) player.displayClientMessage(Component.literal("Set by the server"), true);
     * }</pre>
     *
     * @return {@code true} while on a server that controls this option
     */
    public boolean isServerControlled() {
        return synced && ConfigSync.onRemoteServer() && (serverValue != null || hasFallback);
    }

    void setServerValue(@Nullable T value) {
        this.serverValue = value;
    }

    /**
     * Returns the key in the config file.
     *
     * @return the key in the config file
     */
    public String getName() { return name; }

    /**
     * Returns the description given when the entry was created.
     *
     * @return the description given when the entry was created
     */
    public String getComment() { return comment; }

    /**
     * Returns the tab the option appears in.
     *
     * @return the tab the option appears in
     */
    public String getCategory() { return category; }

    /**
     * Returns the default value.
     *
     * @return the default value
     */
    public T getDefaultValue() { return defaultValue; }

    /**
     * Returns the value class.
     *
     * @return the value class
     */
    public Class<T> getType() { return type; }

    /**
     * Returns the lowest allowed value, or {@code null} without a range.
     *
     * @return the lowest allowed value, or {@code null} without a range
     */
    @Nullable public T getMinValue() { return minValue; }

    /**
     * Returns the highest allowed value, or {@code null} without a range.
     *
     * @return the highest allowed value, or {@code null} without a range
     */
    @Nullable public T getMaxValue() { return maxValue; }

    /**
     * Returns the label set with {@link #withLabel(String)}, or {@code null}.
     *
     * @return the label set with {@link #withLabel(String)}, or {@code null}
     */
    @Nullable public String getLabel() { return label; }

    /**
     * Returns the heading set with {@link #withSection(String)}, or {@code null}.
     *
     * @return the heading set with {@link #withSection(String)}, or {@code null}
     */
    @Nullable public String getSection() { return section; }

    /**
     * Returns the unit set with {@link #withUnit(String)}, or {@code null}.
     *
     * @return the unit set with {@link #withUnit(String)}, or {@code null}
     */
    @Nullable public String getUnit() { return unit; }

    /**
     * Returns the validator set with {@link #withValidator(Predicate)}, or {@code null}.
     *
     * @return the validator set with {@link #withValidator(Predicate)}, or {@code null}
     */
    @Nullable public Predicate<Object> getValidator() { return validator; }

    /**
     * Returns the supplier set with {@link #withElementSupplier(Supplier)}, or {@code null}.
     *
     * @return the supplier set with {@link #withElementSupplier(Supplier)}, or {@code null}
     */
    @Nullable public Supplier<?> getElementSupplier() { return elementSupplier; }

    /**
     * Returns the item check set with {@link #withElementValidator(Predicate)}, or {@code null}.
     *
     * @return the item check set with {@link #withElementValidator(Predicate)}, or {@code null}
     */
    @Nullable public Predicate<Object> getElementValidator() { return elementValidator; }

    /**
     * Returns the list check set with {@link #withSizeValidator(Predicate)}, or {@code null}.
     *
     * @return the list check set with {@link #withSizeValidator(Predicate)}, or {@code null}
     */
    @Nullable public Predicate<List<?>> getSizeValidator() { return sizeValidator; }

    /**
     * Returns whether a list option may be empty, see {@link #withListAllowEmpty(boolean)}.
     *
     * @return whether a list option may be empty, see {@link #withListAllowEmpty(boolean)}
     */
    public boolean isListAllowEmpty() { return listAllowEmpty; }

    /**
     * Whether the value is an enum constant.
     * <pre>{@code
     * if (entry.isEnum()) entry.readEnum("FAST");
     * }</pre>
     *
     * @return {@code true} for enum options
     */
    public boolean isEnum() {
        return type.isEnum() || (defaultValue instanceof Enum);
    }

    /**
     * Whether the value is a {@link List}.
     * <pre>{@code
     * if (entry.isList()) entry.setListFromCommaSeparated("a, b");
     * }</pre>
     *
     * @return {@code true} for list options
     */
    public boolean isList() {
        return List.class.isAssignableFrom(type) || (defaultValue instanceof List);
    }

    /**
     * Whether the value is a {@link Number}.
     * <pre>{@code
     * if (entry.isNumber() && entry.hasRange()) entry.setClamped(10);
     * }</pre>
     *
     * @return {@code true} for number options
     */
    public boolean isNumber() {
        return Number.class.isAssignableFrom(type) || (defaultValue instanceof Number);
    }

    /**
     * Returns how much the slider and arrow keys change the value: the {@link #withStep(double) step}
     * if set, else 0.1 for {@code Double} and {@code Float}, else 1.
     * <pre>{@code
     * double next = RADIUS.get() + RADIUS.getStep();
     * }</pre>
     *
     * @return the step
     */
    public double getStep() {
        if (step != null) return step;
        return type == Double.class || type == Float.class ? 0.1 : 1.0;
    }

    /**
     * Returns the config this entry was added to.
     * <pre>{@code
     * DungeonConfig owner = entry.getOwner();
     * if (owner != null) owner.save();
     * }</pre>
     *
     * @return the config, or {@code null} if the entry hasn't been added to one
     */
    @Nullable
    public DungeonConfig getOwner() {
        return owner;
    }

    /**
     * Returns the dropdown values: the {@link #withChoices(Object[]) choices}, or every constant for enums.
     * <pre>{@code
     * for (Mode mode : MODE.getChoices()) LOGGER.info("Option: {}", mode);
     * }</pre>
     *
     * @return the choices, empty if there are none
     */
    public List<T> getChoices() {
        return choices == null ? List.of() : choices;
    }

    /**
     * Whether the option is shown as a dropdown.
     * <pre>{@code
     * boolean dropdown = FPS.hasChoices();
     * }</pre>
     *
     * @return {@code true} if there is at least one choice
     */
    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }

    /**
     * Whether both a minimum and a maximum were given.
     * <pre>{@code
     * if (RADIUS.hasRange()) LOGGER.info("{}..{}", RADIUS.getMinValue(), RADIUS.getMaxValue());
     * }</pre>
     *
     * @return {@code true} if the option has a range
     */
    public boolean hasRange() {
        return minValue != null && maxValue != null;
    }

    /**
     * Whether {@link #withRegistry(Registry) a registry} provides suggestions.
     * <pre>{@code
     * boolean suggestsIds = ICON.hasRegistry();
     * }</pre>
     *
     * @return {@code true} if a registry is set
     */
    public boolean hasRegistry() {
        return registry != null;
    }

    /**
     * Returns the registry set with {@link #withRegistry(Registry)}, or {@code null}.
     *
     * @return the registry set with {@link #withRegistry(Registry)}, or {@code null}
     */
    @Nullable
    public Registry<?> getRegistry() {
        return registry;
    }

    /**
     * Returns the objects or ids given to {@link #withRegistry(Registry, Collection)}, or {@code null}.
     *
     * @return the objects or ids given to {@link #withRegistry(Registry, Collection)}, or {@code null}
     */
    @Nullable
    public Collection<?> getRegistrySubset() {
        return registrySubset;
    }

    /**
     * Returns the filter set with {@link #withRegistryFilter(Predicate)}, or {@code null}.
     *
     * @return the filter set with {@link #withRegistryFilter(Predicate)}, or {@code null}
     */
    @Nullable
    public Predicate<?> getRegistryFilter() {
        return registryFilter;
    }

    /**
     * Returns the minimum set with {@link #withMinEntries(int)}, or {@code null}.
     *
     * @return the minimum set with {@link #withMinEntries(int)}, or {@code null}
     */
    @Nullable
    public Integer getMinEntries() {
        return minEntries;
    }

    /**
     * Returns the maximum set with {@link #withMaxEntries(int)}, or {@code null}.
     *
     * @return the maximum set with {@link #withMaxEntries(int)}, or {@code null}
     */
    @Nullable
    public Integer getMaxEntries() {
        return maxEntries;
    }

    /**
     * Returns the ids the suggestion popup offers, sorted alphabetically. Worked out on the first call
     * and remembered until the registry, subset or filter changes.
     * <pre>{@code
     * List<String> ids = ICON.getRegistryIds(); // [minecraft:acacia_boat, ...]
     * }</pre>
     *
     * @return the ids, which can't be modified; empty without a registry
     */
    @SuppressWarnings("unchecked")
    public synchronized List<String> getRegistryIds() {
        if (cachedRegistryIds != null) return cachedRegistryIds;
        if (registry == null) return List.of();

        List<String> list = new ArrayList<>();
        if (registrySubset != null && !registrySubset.isEmpty()) {
            for (Object obj : registrySubset) {
                if (obj == null) continue;
                if (obj instanceof String s) {
                    list.add(s);
                } else {
                    Object key = ((Registry<Object>) registry).getKey(obj);
                    list.add(key != null ? key.toString() : obj.toString());
                }
            }
        } else {
            Predicate<Object> filter = (Predicate<Object>) registryFilter;
            for (Object key : registry.keySet()) {
                if (key == null) continue;
                if (filter != null) {
                    try {
                        if (!filter.test(key) && !filter.test(key.toString())) {
                            Object obj = registryValue(key);
                            if (obj == null || !filter.test(obj)) continue;
                        }
                    } catch (Exception ignored) {}
                }
                list.add(key.toString());
            }
        }
        list.sort(String.CASE_INSENSITIVE_ORDER);
        cachedRegistryIds = Collections.unmodifiableList(list);
        return cachedRegistryIds;
    }

    // Looked up reflectively because the lookup method is named differently across Minecraft versions
    @Nullable
    private Object registryValue(Object key) {
        for (String method : new String[]{"get", "getValue"}) {
            try {
                return registry.getClass().getMethod(method, key.getClass()).invoke(registry, key);
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
