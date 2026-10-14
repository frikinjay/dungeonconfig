package com.frikinjay.dungeonconfig.network;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.api.DungeonConfig;
import com.frikinjay.dungeonconfig.api.DungeonConfigs;
import com.frikinjay.dungeonconfig.platform.DcPlatform;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handles multiplayer synchronization of server-mandated configuration values.
 * Transmits synchronized configuration properties to clients upon joining or saving.
 */
public final class ConfigSync {

    private static volatile boolean remote;
    private static volatile boolean connected;
    @Nullable private static volatile MinecraftServer server;

    private ConfigSync() {}

    /**
     * Checks whether the current client session is connected to an external remote dedicated server.
     *
     * @return {@code true} if running as a remote client
     */
    public static boolean onRemoteServer() {
        return remote;
    }

    /**
     * Whether a world is being played: a server (dedicated or integrated) is running, or the client is
     * connected to one. Options that need a {@link com.frikinjay.dungeonconfig.api.RestartType#WORLD world
     * restart} keep their value while this is true.
     *
     * @return {@code true} while in a world
     */
    public static boolean inWorld() {
        return server != null || connected;
    }

    public static void serverStarted(MinecraftServer started) {
        server = started;
    }

    public static void serverStopped() {
        server = null;
        if (!connected) DungeonConfigs.releaseWorldRestartValues();
    }

    @Nullable
    public static MinecraftServer server() {
        return server;
    }

    public static void playerJoined(ServerPlayer player) {
        for (DungeonConfig config : DungeonConfigs.all()) send(player, config);
    }

    private static void send(ServerPlayer player, DungeonConfig config) {
        if (!config.hasSyncedEntries()) return;
        try {
            DcPlatform.trySend(player, new SyncPayload(config.modId(), config.syncedValues().toString()));
        } catch (Exception e) {
            DungeonConfigMod.LOGGER.warn("[DungeonConfig] Couldn't send {} settings to {}", config.modId(), player.getName().getString(), e);
        }
    }

    /**
     * Resends synchronized configuration values to all active players on the hosting server.
     *
     * @param config the configuration whose values were updated
     */
    public static void resend(DungeonConfig config) {
        MinecraftServer current = server;
        if (current == null || !config.hasSyncedEntries()) return;
        try {
            current.execute(() -> {
                if (!current.isSameThread() || !current.isRunning()) return;
                for (ServerPlayer player : List.copyOf(current.getPlayerList().getPlayers())) send(player, config);
            });
        } catch (Exception e) {
            DungeonConfigMod.LOGGER.warn("[DungeonConfig] Couldn't resend {} settings", config.modId(), e);
        }
    }

    public static void clientJoined(boolean remoteServer) {
        remote = remoteServer;
        connected = true;
    }

    public static void clientLeft() {
        remote = false;
        connected = false;
        for (DungeonConfig config : DungeonConfigs.all()) config.clearServerValues();
        if (server == null) DungeonConfigs.releaseWorldRestartValues();
    }

    public static void received(SyncPayload payload) {
        DungeonConfig config = DungeonConfigs.get(payload.modId());
        if (config == null) return;
        try {
            JsonElement json = JsonParser.parseString(payload.json());
            if (json.isJsonObject()) config.applyServerValues(json.getAsJsonObject());
        } catch (RuntimeException e) {
            DungeonConfigMod.LOGGER.warn("[DungeonConfig] The server sent unreadable {} settings: {}", payload.modId(), e.toString());
        }
    }
}
