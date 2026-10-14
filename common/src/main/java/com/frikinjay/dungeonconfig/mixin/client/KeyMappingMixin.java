package com.frikinjay.dungeonconfig.mixin.client;

import com.frikinjay.dungeonconfig.client.ConfigKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Shadow
    public abstract String getName();

    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void dungeonconfig$ctrlPrefix(CallbackInfoReturnable<Component> cir) {
        if (this.getName().equals(ConfigKey.NAME)) {
            cir.setReturnValue(Component.translatableWithFallback("dungeonconfig.key.ctrl_combo", "Ctrl + %s", cir.getReturnValue()));
        }
    }
}
