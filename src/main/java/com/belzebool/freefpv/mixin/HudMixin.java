package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.MixinTargets;
import com.belzebool.freefpv.client.tools.ToolSlot;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} elif >=1.21.6
//import com.mojang.blaze3d.pipeline.RenderPipeline;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
//import net.minecraft.client.gui.GuiGraphics;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else
//import net.minecraft.client.gui.Gui;

/** While the tool slot is active the hotbar doesn't also highlight its own slot. Cosmetic, so never required. */
@Mixin(/*? if >=26.2 {*/Hud/*?} else {*//*Gui*//*?}*/.class)
public abstract class HudMixin {
    @WrapWithCondition(method = MixinTargets.HOTBAR_METHOD, at = @At(value = "INVOKE", target = MixinTargets.HOTBAR_BLIT, ordinal = 1), require = 0)
    private boolean freefpv$hideSelection(/*? if >=26.1 {*/GuiGraphicsExtractor/*?} else {*//*GuiGraphics*//*?}*/ graphics,
                                          /*? if >=1.21.6 {*/RenderPipeline pipeline, /*?}*/Identifier sprite, int x, int y, int w, int h) {
        return !ToolSlot.INSTANCE.hidesHotbarSelection();
    }
}
