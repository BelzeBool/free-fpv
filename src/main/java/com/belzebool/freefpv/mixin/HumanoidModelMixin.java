package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.PilotPose;
import com.belzebool.freefpv.client.PilotPoseState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both hands on the remote while flying. Runs for the body and for armour layers, so armour follows. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void freefpv$pilotPose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof PilotPoseState pilot && pilot.freefpv$pilotMode() != 0) {
            PilotPose.apply((HumanoidModel<?>) (Object) this, pilot.freefpv$pilotMode());
        }
    }
}
