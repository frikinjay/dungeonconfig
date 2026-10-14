package com.frikinjay.dungeonconfig.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Loader specific services, implemented by {@code DcPlatformImpl} in the fabric and neoforge modules.
 */
public final class DcPlatform {

    private DcPlatform() {}

    /**
     * Paths inside a mod's jar that are tried, in order, when its metadata names no usable icon.
     */
    public static List<String> iconCandidates(String modId) {
        return List.of("icon.png", "icon.jpg", "icon.jpeg",
                modId + ".png", modId + ".jpg", modId + ".jpeg",
                "assets/" + modId + "/icon.png", "assets/" + modId + "/" + modId + ".png");
    }

    @ExpectPlatform
    public static Path gameDirectory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @Nullable
    public static String modName(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @Nullable
    public static String modDescription(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @Nullable
    public static String modVersion(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @Nullable
    public static String modAuthors(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @Nullable
    public static String modLicense(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean trySend(ServerPlayer player, CustomPacketPayload payload) {
        throw new AssertionError();
    }

    @ExpectPlatform
    @Nullable
    public static InputStream openModIcon(String modId) {
        throw new AssertionError();
    }
}
