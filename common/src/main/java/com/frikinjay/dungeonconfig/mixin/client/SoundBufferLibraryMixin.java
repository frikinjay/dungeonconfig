package com.frikinjay.dungeonconfig.mixin.client;

import com.frikinjay.dungeonconfig.client.ui.DungeonsMusic;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void dungeonconfig$menuMusic(Identifier location, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        AudioStream stream = DungeonsMusic.takeStream(location.getNamespace(), location.getPath());
        if (stream != null) cir.setReturnValue(CompletableFuture.completedFuture(stream));
    }
}
