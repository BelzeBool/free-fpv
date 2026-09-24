package com.belzebool.freefpv.core.osd;

/** Minimal 2D drawing surface in GUI pixels. Implemented per Minecraft version on top of its GUI renderer. */
public interface OsdCanvas {
    int width();

    int height();

    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String text, int x, int y, int argb, boolean shadow);

    int textWidth(String text);

    int lineHeight();

    void push();

    void pop();

    void translate(float x, float y);

    void rotate(float radians);

    void scale(float s);

    /** Draws a GUI sprite ("namespace:path" under textures/gui/sprites) stretched to the given size. */
    void sprite(String id, int x, int y, int w, int h);

    /** Draws a 16x16 item icon for an item model id ("namespace:path"), like a hotbar slot does. */
    void icon(String itemModel, int x, int y);

    default void centeredText(String text, int cx, int y, int argb, boolean shadow) {
        text(text, cx - textWidth(text) / 2, y, argb, shadow);
    }

    default void rightText(String text, int right, int y, int argb, boolean shadow) {
        text(text, right - textWidth(text), y, argb, shadow);
    }

    default void hLine(int x0, int x1, int y, int argb) {
        fill(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 1, argb);
    }

    default void vLine(int x, int y0, int y1, int argb) {
        fill(x, Math.min(y0, y1), x + 1, Math.max(y0, y1) + 1, argb);
    }

    default void frame(int x0, int y0, int x1, int y1, int argb) {
        hLine(x0, x1 - 1, y0, argb);
        hLine(x0, x1 - 1, y1 - 1, argb);
        vLine(x0, y0, y1 - 1, argb);
        vLine(x1 - 1, y0, y1 - 1, argb);
    }
}
