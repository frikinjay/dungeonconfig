package com.frikinjay.dungeonconfig.mixin.client;

import com.frikinjay.dungeonconfig.client.ConfigKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V"))
    private void dungeonconfig$addKey(CallbackInfo ci) {
        keyMappings = Arrays.copyOf(keyMappings, keyMappings.length + 1);
        keyMappings[keyMappings.length - 1] = ConfigKey.create();
    }
}
