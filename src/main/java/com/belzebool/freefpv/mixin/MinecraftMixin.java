package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pilot's body stays put: no attacking, using items, hotbar switching or perspective toggling while flying. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow @Final public Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void freefpv$blockGameplayKeys(CallbackInfo ci) {
        if (DroneController.INSTANCE.isFlying()) DroneController.INSTANCE.drainGameplayKeys(options);
    }
}
