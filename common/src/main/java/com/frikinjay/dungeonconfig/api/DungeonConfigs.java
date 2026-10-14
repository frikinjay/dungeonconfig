package com.frikinjay.dungeonconfig.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@link DungeonConfig} that has been built. {@link DungeonConfig.Builder#build()} adds configs
 * here automatically; there is no way to remove one.
 * <pre>{@code
 * // Reload another mod's settings, if that mod is installed
 * DungeonConfig other = DungeonConfigs.get("othermod");
 * if (other != null) other.reload();
 *
 * // List every mod that uses Dungeon Config
 * for (DungeonConfig config : DungeonConfigs.all()) {
 *     LOGGER.info("{} has {} settings", config.displayName(), config.entries().size());
 * }
 * }</pre>
 */
public final class DungeonConfigs {

    private static final Map<String, DungeonConfig> CONFIGS = new LinkedHashMap<>();

    private DungeonConfigs() {}

    static synchronized void register(DungeonConfig config) {
        if (CONFIGS.containsKey(config.modId())) {
            throw new IllegalStateException("A Dungeon Config for " + config.modId() + " is already registered");
        }
        CONFIGS.put(config.modId(), config);
    }

    /**
     * Returns every config in the order they were built.
     * <pre>{@code
     * int mods = DungeonConfigs.all().size();
     * }</pre>
     *
     * @return a new list; changing it doesn't change the registry
     */
    public static synchronized List<DungeonConfig> all() {
        return new ArrayList<>(CONFIGS.values());
    }

    /**
     * Returns every config sorted by display name, ignoring case, as on the Dungeon Configs page.
     * <pre>{@code
     * for (DungeonConfig config : DungeonConfigs.forList()) menu.add(config.displayName());
     * }</pre>
     *
     * @return a new, sorted list
     */
    public static synchronized List<DungeonConfig> forList() {
        List<DungeonConfig> list = new ArrayList<>(CONFIGS.values());
        list.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return list;
    }

    /**
     * Lets every option marked {@link RestartType#WORLD} pick up its saved value again. Dungeon Config
     * calls it when a world or server is left.
     * <pre>{@code
     * DungeonConfigs.releaseWorldRestartValues();
     * }</pre>
     */
    public static synchronized void releaseWorldRestartValues() {
        for (DungeonConfig config : CONFIGS.values()) {
            for (ConfigEntry<?> entry : config.entries()) entry.releaseHeldValue(RestartType.WORLD);
        }
    }

    /**
     * Finds the config of a mod.
     * <pre>{@code
     * DungeonConfig sanctum = DungeonConfigs.get("sanctum");
     * }</pre>
     *
     * @param modId the mod id the config was built with
     * @return the config, or {@code null} if that mod has none (or isn't installed)
     */
    @Nullable
    public static synchronized DungeonConfig get(String modId) {
        return CONFIGS.get(modId);
    }
}
