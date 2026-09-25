package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.PilotPose;
import com.belzebool.freefpv.client.PilotPoseState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks players who are flying a drone and gives them the remote to hold. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void freefpv$pilot(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        int mode = PilotPose.modeFor(entity.getId());
        ((PilotPoseState) state).freefpv$setPilotMode(mode);
        if (mode != 0) PilotPose.holdRemote(entity, state, mode);
    }
}
