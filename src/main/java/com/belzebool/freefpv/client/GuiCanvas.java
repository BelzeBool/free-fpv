package com.belzebool.freefpv.client;

import com.belzebool.freefpv.core.osd.OsdCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
//import net.minecraft.client.gui.GuiGraphics;

/** {@link OsdCanvas} on top of the vanilla GUI renderer. */
public final class GuiCanvas implements OsdCanvas {
    //? if >=26.1 {
    private final GuiGraphicsExtractor g;
    //?} else
    //private final GuiGraphics g;
    private final Font font = Minecraft.getInstance().font;

    public GuiCanvas(/*? if >=26.1 {*/GuiGraphicsExtractor/*?} else {*//*GuiGraphics*//*?}*/ g) {
        this.g = g;
    }

    @Override
    public int width() {
        return g.guiWidth();
    }

    @Override
    public int height() {
        return g.guiHeight();
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        g.text(font, text, x, y, argb, shadow);
        //?} else
        //g.drawString(font, text, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void push() {
        g.pose().pushMatrix();
    }

    @Override
    public void pop() {
        g.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    @Override
    public void rotate(float radians) {
        g.pose().rotate(radians);
    }

    @Override
    public void scale(float s) {
        g.pose().scale(s);
    }
}
