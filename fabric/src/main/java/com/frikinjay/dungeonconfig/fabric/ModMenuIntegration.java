package com.frikinjay.dungeonconfig.fabric;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.api.DungeonConfig;
import com.frikinjay.dungeonconfig.api.DungeonConfigs;
import com.frikinjay.dungeonconfig.client.screen.DungeonConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import java.util.HashMap;
import java.util.Map;

public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DungeonConfigScreen::list;
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();
        for (DungeonConfig config : DungeonConfigs.all()) {
            if (config.modId().equals(DungeonConfigMod.MOD_ID)) continue;
            factories.put(config.modId(), parent -> new DungeonConfigScreen(parent, config));
        }
        return factories;
    }
}
