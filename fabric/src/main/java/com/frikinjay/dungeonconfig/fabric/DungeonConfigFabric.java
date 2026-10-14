package com.frikinjay.dungeonconfig.fabric;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.network.ConfigSync;
import com.frikinjay.dungeonconfig.network.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class DungeonConfigFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        DungeonConfigMod.init();
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ConfigSync.playerJoined(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(ConfigSync::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ConfigSync.serverStopped());
    }
}
