package com.frikinjay.dungeonconfig.platform.fabric;

import com.frikinjay.dungeonconfig.platform.DcPlatform;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DcPlatformImpl {

    private DcPlatformImpl() {}

    public static Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    private static Optional<ModContainer> mod(String modId) {
        return FabricLoader.getInstance().getModContainer(modId);
    }

    @Nullable
    public static String modName(String modId) {
        return mod(modId).map(c -> c.getMetadata().getName()).orElse(null);
    }

    @Nullable
    public static String modDescription(String modId) {
        return mod(modId).map(c -> c.getMetadata().getDescription()).orElse(null);
    }

    @Nullable
    public static String modVersion(String modId) {
        return mod(modId).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    @Nullable
    public static String modAuthors(String modId) {
        return mod(modId).map(c -> {
            var authors = c.getMetadata().getAuthors();
            if (authors == null || authors.isEmpty()) return null;
            String joined = authors.stream()
                    .map(Person::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.joining(", "));
            return joined.isBlank() ? null : joined;
        }).orElse(null);
    }

    @Nullable
    public static String modLicense(String modId) {
        return mod(modId).map(c -> {
            var licenses = c.getMetadata().getLicense();
            if (licenses == null || licenses.isEmpty()) return null;
            String joined = String.join(", ", licenses).trim();
            return joined.isBlank() ? null : joined;
        }).orElse(null);
    }

    public static boolean trySend(ServerPlayer player, CustomPacketPayload payload) {
        if (!ServerPlayNetworking.canSend(player, payload.type())) return false;
        ServerPlayNetworking.send(player, payload);
        return true;
    }

    @Nullable
    public static InputStream openModIcon(String modId) {
        Optional<ModContainer> opt = mod(modId);
        if (opt.isEmpty()) return null;
        ModContainer container = opt.get();
        List<String> paths = new ArrayList<>();
        container.getMetadata().getIconPath(128).ifPresent(paths::add);
        paths.addAll(DcPlatform.iconCandidates(modId));
        for (String path : paths) {
            Optional<Path> p = container.findPath(path);
            if (p.isPresent() && Files.isRegularFile(p.get())) {
                try {
                    return Files.newInputStream(p.get());
                } catch (IOException ignored) {}
            }
        }
        return null;
    }
}
