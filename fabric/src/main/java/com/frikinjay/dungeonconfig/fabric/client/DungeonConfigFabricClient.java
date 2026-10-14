package com.frikinjay.dungeonconfig.fabric.client;

import com.frikinjay.dungeonconfig.network.ConfigSync;
import com.frikinjay.dungeonconfig.network.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class DungeonConfigFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> context.client().execute(() -> ConfigSync.received(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ConfigSync.clientJoined(!client.isLocalServer()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ConfigSync.clientLeft());
    }
}
