package com.frikinjay.dungeonconfig.mixin.client;

import com.frikinjay.dungeonconfig.client.ConfigKey;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void dungeonconfig$openKey(CallbackInfo ci) {
        ConfigKey.tick();
    }
}
