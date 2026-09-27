package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if <26.3 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
*///?}

/**
 * FPV video look (lens and colour post effect) and, before 26.1, the drone field of view. The post effect is never
 * required: if a version moves things around, flying still works, just without the lens.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    //? if >=26.3 {
    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void freefpv$fpvLens(CallbackInfo ci) {
        Identifier effect = DroneController.INSTANCE.videoEffect();
        if (effect != null) ((GameRenderer) (Object) this).getRequestedPostEffects().add(effect);
    }
    //?} else {
    /*@ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;postEffectId:Lnet/minecraft/resources/Identifier;"), require = 0)
    private Identifier freefpv$fpvLens(Identifier original) {
        Identifier effect = DroneController.INSTANCE.videoEffect();
        return effect != null ? effect : original;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;effectActive:Z"), require = 0)
    private boolean freefpv$fpvLensActive(boolean original) {
        return original || DroneController.INSTANCE.videoEffect() != null;
    }
    *///?}

    //? if <26.1 {
    /*@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void freefpv$droneFov(CallbackInfoReturnable</^? if >=1.21.2 {^/Float/^?} else {^//^Double^//^?}^/> cir) {
        if (DroneController.INSTANCE.isFlying()) cir.setReturnValue(/^? if >=1.21.2 {^/(float)/^?} else {^//^(double)^//^?}^/ DroneController.INSTANCE.verticalFov());
    }
    *///?}
}
