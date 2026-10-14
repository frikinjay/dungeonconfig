package com.frikinjay.dungeonconfig.neoforge;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.network.ConfigSync;
import com.frikinjay.dungeonconfig.network.SyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(DungeonConfigMod.MOD_ID)
public final class DungeonConfigNeoForge {

    public DungeonConfigNeoForge(IEventBus modBus) {
        DungeonConfigMod.init();
        modBus.addListener(DungeonConfigNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) ConfigSync.playerJoined(player);
        });
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> ConfigSync.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> ConfigSync.serverStopped());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DungeonConfigMod.MOD_ID).optional();
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> ConfigSync.received(payload)));
    }
}
