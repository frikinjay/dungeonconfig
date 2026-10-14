package com.frikinjay.dungeonconfig.client;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.client.screen.DungeonConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public final class TitleButton extends Button.Plain {

    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(DungeonConfigMod.MOD_ID, "config_button"),
            Identifier.fromNamespaceAndPath(DungeonConfigMod.MOD_ID, "config_button_disabled"),
            Identifier.fromNamespaceAndPath(DungeonConfigMod.MOD_ID, "config_button_highlighted"),
            Identifier.fromNamespaceAndPath(DungeonConfigMod.MOD_ID, "config_button_disabled"));

    private TitleButton(int x, int y, Component message, OnPress onPress) {
        super(x, y, 20, 20, message, onPress, supplier -> (MutableComponent) supplier.get());
    }

    public static Button create(int x, int y, Screen parent) {
        Component label = Component.translatableWithFallback("dungeonconfig.screen.list_title", "Dungeon Configs");
        TitleButton button = new TitleButton(x, y, label, b -> Minecraft.getInstance().setScreen(DungeonConfigScreen.list(parent)));
        button.setTooltip(Tooltip.create(label));
        return button;
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Identifier sprite = SPRITES.get(this.active, this.isHoveredOrFocused());
        int color = ((int) (this.alpha * 255.0F) << 24) | 0xFFFFFF;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height, color);
    }
}
