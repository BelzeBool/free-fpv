package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.PilotPoseState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements PilotPoseState {
    @Unique
    private int freefpv$pilotMode;

    @Override
    public int freefpv$pilotMode() {
        return freefpv$pilotMode;
    }

    @Override
    public void freefpv$setPilotMode(int mode) {
        freefpv$pilotMode = mode;
    }
}
