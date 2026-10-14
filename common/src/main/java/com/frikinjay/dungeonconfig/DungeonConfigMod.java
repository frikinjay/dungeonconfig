package com.frikinjay.dungeonconfig;

import com.frikinjay.dungeonconfig.api.ConfigEntry;
import com.frikinjay.dungeonconfig.api.DungeonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point and core configuration registry for Dungeon Config.
 */
public final class DungeonConfigMod {

    public static final String MOD_ID = "dungeonconfig";
    public static final Logger LOGGER = LoggerFactory.getLogger("DungeonConfig");

    /**
     * Internal configuration container for Dungeon Config itself.
     * Serves as the fallback provider for screen themes, media backgrounds, and music.
     */
    public static final DungeonConfig CONFIG = DungeonConfig.builder(MOD_ID).displayName("Dungeon Config").build();

    public static final ConfigEntry<Boolean> TITLE_BUTTON = CONFIG.addBoolean("titleScreenButton", "General", true,
            "Show the Dungeon Configs button next to Singleplayer on the title screen");

    private static boolean initialized;

    private DungeonConfigMod() {}

    /**
     * Initializes the mod runtime and loads default configuration settings.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        CONFIG.load();
    }
}
