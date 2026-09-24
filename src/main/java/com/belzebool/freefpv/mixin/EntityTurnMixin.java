package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While flying, mouse movement steers the drone instead of turning the pilot's head. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void freefpv$steerDrone(double xo, double yo, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && DroneController.INSTANCE.isFlying()) {
            DroneController.INSTANCE.onMouseTurn(xo, yo);
            ci.cancel();
        }
    }
}
