package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.Compat;
import com.belzebool.freefpv.client.DroneController;
import com.belzebool.freefpv.client.tools.ToolSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scroll wheel: zooms the camera drone while flying, steps through the tool wheel while it is open, and lets the
 * hotbar scroll into the tool slot between slot 9 and slot 1.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void freefpv$zoom(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!Compat.inWorldView(mc)) return;
        if (DroneController.INSTANCE.isFlying()) {
            DroneController.INSTANCE.onScroll(yoffset);
            ci.cancel();
        } else if (ToolSlot.INSTANCE.onScroll(yoffset)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void freefpv$toolSlotScroll(Inventory inventory, int slot, Operation<Void> original) {
        if (!ToolSlot.INSTANCE.onHotbarScroll(inventory, inventory.getSelectedSlot(), slot)) original.call(inventory, slot);
    }
}
