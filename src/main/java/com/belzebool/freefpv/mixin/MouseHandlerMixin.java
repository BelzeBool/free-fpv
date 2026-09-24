package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll wheel zooms the camera drone instead of changing the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void freefpv$zoom(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        boolean idle = mc.gui.screen() == null && mc.gui.overlay() == null;
        //?} else
        //boolean idle = mc.screen == null && mc.getOverlay() == null;
        if (DroneController.INSTANCE.isFlying() && idle) {
            DroneController.INSTANCE.onScroll(yoffset);
            ci.cancel();
        }
    }
}
