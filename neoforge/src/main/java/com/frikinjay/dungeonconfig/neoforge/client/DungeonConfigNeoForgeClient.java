package com.frikinjay.dungeonconfig.neoforge.client;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.api.DungeonConfig;
import com.frikinjay.dungeonconfig.api.DungeonConfigs;
import com.frikinjay.dungeonconfig.client.screen.DungeonConfigScreen;
import com.frikinjay.dungeonconfig.network.ConfigSync;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = DungeonConfigMod.MOD_ID, dist = Dist.CLIENT)
public final class DungeonConfigNeoForgeClient {

    public DungeonConfigNeoForgeClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (client, parent) -> DungeonConfigScreen.list(parent));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
                event -> ConfigSync.clientJoined(!Minecraft.getInstance().isLocalServer()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ConfigSync.clientLeft());
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(DungeonConfigNeoForgeClient::registerScreens));
    }

    private static void registerScreens() {
        for (DungeonConfig config : DungeonConfigs.all()) {
            if (config.modId().equals(DungeonConfigMod.MOD_ID)) continue;
            ModList.get().getModContainerById(config.modId()).ifPresent(mod -> {
                if (mod.getCustomExtension(IConfigScreenFactory.class).isEmpty()) {
                    mod.registerExtensionPoint(IConfigScreenFactory.class, (client, parent) -> new DungeonConfigScreen(parent, config));
                }
            });
        }
    }
}
