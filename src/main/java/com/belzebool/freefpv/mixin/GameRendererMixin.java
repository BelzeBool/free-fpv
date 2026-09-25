package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Before 26.1 the field of view lives in GameRenderer; later versions are handled in {@link CameraMixin}. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    //? if <26.1 {
    /*@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void freefpv$droneFov(CallbackInfoReturnable</^? if >=1.21.2 {^/Float/^?} else {^//^Double^//^?}^/> cir) {
        if (DroneController.INSTANCE.isFlying()) cir.setReturnValue(/^? if >=1.21.2 {^/(float)/^?} else {^//^(double)^//^?}^/ DroneController.INSTANCE.verticalFov());
    }
    *///?}
}
