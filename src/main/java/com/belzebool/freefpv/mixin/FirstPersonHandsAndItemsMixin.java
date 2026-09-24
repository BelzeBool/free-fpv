package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.tools.ToolSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** With the tool slot active the first-person hand shows the tool, with the vanilla lower-and-raise swap animation. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack freefpv$toolInHand(LocalPlayer player, Operation<ItemStack> original) {
        ItemStack tool = ToolSlot.INSTANCE.heldStack();
        return tool != null ? tool : original.call(player);
    }
}
