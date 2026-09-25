package com.belzebool.freefpv.core.tool;

/**
 * Radial tool picker state: a virtual cursor driven by mouse deltas (the real cursor stays grabbed, so the player can
 * keep walking), the open/close animation and which slot the cursor points at. Coordinates are GUI pixels relative to
 * the wheel centre; angles are degrees clockwise from straight up.
 */
public final class ToolWheel {
    public static final double RADIUS = 56;
    /** Inside this distance from the centre nothing is hovered, so a quick tap keeps the current tool. */
    public static final double DEAD_ZONE = 14;

    private double cursorX, cursorY;
    private double open;
    private boolean visible;

    public void show() {
        if (!visible) {
            visible = true;
            cursorX = cursorY = 0;
        }
    }

    public void hide() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    /** 0..1, eased; the wheel keeps drawing while it animates closed. */
    public double openAmount() {
        return open * open * (3 - 2 * open);
    }

    public boolean isDrawn() {
        return visible || open > 0.001;
    }

    public void tick(double dt) {
        open = Math.max(0, Math.min(1, open + (visible ? dt / 0.12 : -dt / 0.09)));
    }

    /** Mouse movement in GUI pixels. The cursor is clamped to just outside the ring. */
    public void moveCursor(double dx, double dy) {
        cursorX += dx;
        cursorY += dy;
        double max = RADIUS + 12;
        double len = Math.hypot(cursorX, cursorY);
        if (len > max) {
            cursorX *= max / len;
            cursorY *= max / len;
        }
    }

    /** Snaps the cursor onto a slot, used by scroll wheel and gamepad navigation. */
    public void pointAt(int index, int count) {
        double a = Math.toRadians(angleOf(index, count));
        cursorX = Math.sin(a) * RADIUS * 0.8;
        cursorY = -Math.cos(a) * RADIUS * 0.8;
    }

    public double cursorX() {
        return cursorX;
    }

    public double cursorY() {
        return cursorY;
    }

    /** Index of the slot under the cursor, or -1 inside the dead zone. */
    public int hovered(int count) {
        if (count <= 0 || Math.hypot(cursorX, cursorY) < DEAD_ZONE) return -1;
        double angle = Math.toDegrees(Math.atan2(cursorX, -cursorY));
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double diff = Math.abs(((angleOf(i, count) - angle) % 360 + 540) % 360 - 180);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    /** Where slot {@code i} of {@code count} sits: two tools go left and right, more go round from the top. */
    public static double angleOf(int i, int count) {
        if (count == 1) return 0;
        if (count == 2) return i == 0 ? 270 : 90;
        return i * 360.0 / count;
    }
}
