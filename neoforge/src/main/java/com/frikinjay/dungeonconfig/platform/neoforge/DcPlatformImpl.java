package com.frikinjay.dungeonconfig.platform.neoforge;

import com.frikinjay.dungeonconfig.platform.DcPlatform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DcPlatformImpl {

    private DcPlatformImpl() {}

    public static Path gameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    public static boolean isModLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    private static Optional<? extends ModContainer> mod(String modId) {
        ModList list = ModList.get();
        return list == null ? Optional.empty() : list.getModContainerById(modId);
    }

    @Nullable
    public static String modName(String modId) {
        return mod(modId).map(c -> c.getModInfo().getDisplayName()).orElse(null);
    }

    @Nullable
    public static String modDescription(String modId) {
        return mod(modId).map(c -> c.getModInfo().getDescription()).orElse(null);
    }

    @Nullable
    public static String modAuthors(String modId) {
        IModInfo info = info(modId);
        if (info == null) return null;
        String authors = formatAuthors(firstElement(info.getConfig(), "authors", "mod_authors", "authorList"));
        if (authors == null && info.getModProperties() != null) {
            Object property = info.getModProperties().get("authors");
            if (property == null) property = info.getModProperties().get("mod_authors");
            authors = formatAuthors(property);
        }
        if (authors == null && info.getOwningFile() != null) {
            authors = formatAuthors(firstElement(info.getOwningFile().getConfig(), "authors", "mod_authors"));
        }
        return authors;
    }

    @Nullable
    private static String formatAuthors(@Nullable Object obj) {
        if (obj instanceof Collection<?> col) {
            String joined = col.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
            return joined.isBlank() ? null : joined;
        }
        return text(obj);
    }

    @Nullable
    public static String modLicense(String modId) {
        IModInfo info = info(modId);
        if (info == null) return null;
        String license = info.getOwningFile() == null ? null : text(info.getOwningFile().getLicense());
        if (license == null) license = text(firstElement(info.getConfig(), "license"));
        if (license == null && info.getModProperties() != null) license = text(info.getModProperties().get("license"));
        return license;
    }

    @Nullable
    private static IModInfo info(String modId) {
        return mod(modId).map(ModContainer::getModInfo).orElse(null);
    }

    // Metadata layouts differ between mods, so a broken one is skipped rather than failing the lookup
    @Nullable
    private static Object firstElement(@Nullable IConfigurable config, String... keys) {
        if (config == null) return null;
        try {
            for (String key : keys) {
                Optional<Object> value = config.getConfigElement(key);
                if (value.isPresent()) return value.get();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Nullable
    private static String text(@Nullable Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    @Nullable
    public static String modVersion(String modId) {
        Optional<? extends ModContainer> container = mod(modId);
        if (container.isEmpty()) return null;
        Object version = container.get().getModInfo().getVersion();
        return String.valueOf(version);
    }

    public static boolean trySend(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload.type())) return false;
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    @Nullable
    public static InputStream openModIcon(String modId) {
        IModInfo info = info(modId);
        if (info == null) return null;
        var contents = info.getOwningFile().getFile().getContents();
        List<String> paths = new ArrayList<>();
        info.getLogoFile().map(String::trim).filter(logo -> !logo.isEmpty())
                .ifPresent(logo -> paths.add(logo.startsWith("/") ? logo.substring(1) : logo));
        paths.addAll(DcPlatform.iconCandidates(modId));
        for (String path : paths) {
            if (contents.containsFile(path)) {
                try {
                    return contents.openFile(path);
                } catch (IOException ignored) {}
            }
        }
        return null;
    }
}
