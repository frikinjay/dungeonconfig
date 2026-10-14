package com.frikinjay.dungeonconfig.mixin.client;

import com.frikinjay.dungeonconfig.DungeonConfigMod;
import com.frikinjay.dungeonconfig.client.TitleButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dungeonconfig$addButton(CallbackInfo ci) {
        if (!DungeonConfigMod.TITLE_BUTTON.getValue()) return;
        this.children().stream()
                .filter(w -> w instanceof Button b && b.getMessage().equals(Component.translatable("menu.singleplayer")))
                .findFirst()
                .ifPresent(widget -> {
                    AbstractWidget singleplayer = (AbstractWidget) widget;
                    int x = singleplayer.getX() + singleplayer.getWidth() + 4;
                    int y = singleplayer.getY();
                    Button button = TitleButton.create(x, y, this);
                    button.setAlpha(singleplayer.getAlpha());
                    this.addRenderableWidget(button);
                });
    }
}
