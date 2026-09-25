package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server-side model of your own drone would sit right on your camera lens; skip it while flying. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void freefpv$hideOwnDrone(E entity, Frustum culler, double camX, double camY, double camZ,
                                                        /*? if >=26.3 {*/float partialTicks, /*?}*/CallbackInfoReturnable<Boolean> cir) {
        if (DroneController.INSTANCE.shouldHide(entity)) cir.setReturnValue(false);
    }
}
