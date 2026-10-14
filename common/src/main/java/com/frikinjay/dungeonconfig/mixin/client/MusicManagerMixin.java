package com.frikinjay.dungeonconfig.mixin.client;

import com.frikinjay.dungeonconfig.client.ui.DungeonsMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dungeonconfig$waitForMenuMusic(CallbackInfo ci) {
        if (DungeonsMusic.active()) ci.cancel();
    }
}
