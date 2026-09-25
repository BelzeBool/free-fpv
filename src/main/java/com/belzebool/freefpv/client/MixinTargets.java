package com.belzebool.freefpv.client;

/** Mixin target strings that differ between Minecraft versions. Compile-time constants, inlined into the mixins. */
public final class MixinTargets {
    //? if >=26.1 {
    public static final String HOTBAR_METHOD = "extractItemHotbar";
    public static final String GUI_GRAPHICS = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;";
    //?} else {
    /*public static final String HOTBAR_METHOD = "renderItemHotbar";
    public static final String GUI_GRAPHICS = "Lnet/minecraft/client/gui/GuiGraphics;";
    *///?}

    //? if >=26.3 {
    private static final String PIPELINE = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;";
    //?} elif >=1.21.6 {
    /*private static final String PIPELINE = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;";
    *///?} else
    //private static final String PIPELINE = "";

    /** The hotbar selection frame is the second blitSprite call in the hotbar method on every version. */
    public static final String HOTBAR_BLIT = GUI_GRAPHICS + "blitSprite(" + PIPELINE + "Lnet/minecraft/resources/Identifier;IIII)V";

    private MixinTargets() {
    }
}
