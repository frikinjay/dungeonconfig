package com.frikinjay.dungeonconfig.client;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.client.screen.DungeonConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ConfigKey {

    public static final String NAME = "key.dungeonconfig.open";
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(DungeonConfigMod.MOD_ID, "dungeonconfig"));
    private static KeyMapping key;

    private ConfigKey() {}

    public static KeyMapping create() {
        if (key == null) key = new KeyMapping(NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
        return key;
    }

    public static void tick() {
        if (key == null) return;
        Minecraft mc = Minecraft.getInstance();
        while (key.consumeClick()) {
            if ((!key.isDefault() || mc.hasControlDown()) && mc.screen == null) mc.setScreen(DungeonConfigScreen.list(null));
        }
    }
}
