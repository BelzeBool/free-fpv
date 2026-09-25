package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.tools.ToolSlot;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the tool slot is active the hotbar doesn't also highlight its own slot. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @WrapWithCondition(method = "extractItemHotbar", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
        ordinal = 1))
    private boolean freefpv$hideSelection(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        return !ToolSlot.INSTANCE.hidesHotbarSelection();
    }
}
