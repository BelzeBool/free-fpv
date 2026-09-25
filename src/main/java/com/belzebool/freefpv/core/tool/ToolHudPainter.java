package com.belzebool.freefpv.core.tool;

import com.belzebool.freefpv.core.osd.OsdCanvas;

import java.util.ArrayList;
import java.util.List;

/** Draws the 10th tool slot next to the hotbar and the radial tool wheel. Pure layout on top of {@link OsdCanvas}. */
public final class ToolHudPainter {
    public static final String SLOT = "freefpv:tool/slot";
    public static final String SLOT_SELECTED = "freefpv:tool/slot_selected";
    public static final String WHEEL_SLOT = "freefpv:tool/wheel_slot";

    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int YELLOW = 0xFFFF55;

    /** One tool as the wheel shows it. {@code status} is a short line such as the current flight mode. */
    public record Entry(String name, String description, String icon, String status, boolean locked) {
    }

    private ToolHudPainter() {
    }

    /** The tool slot, drawn like a hotbar slot, with the hotbar's selection frame while it is active. */
    public static void slot(OsdCanvas c, int x, int y, String icon, boolean active) {
        c.sprite(SLOT, x + 1, y + 1, 22, 22);
        if (icon != null) c.icon(icon, x + 4, y + 4);
        if (active) c.sprite(SLOT_SELECTED, x, y, 24, 24);
    }

    /** Name of the tool just picked, fading out above the hotbar like the vanilla held-item name. */
    public static void toolName(OsdCanvas c, String text, double alpha, int y) {
        if (alpha <= 0.02 || text == null || text.isEmpty()) return;
        int a = Math.max(4, (int) (Math.min(1, alpha) * 255));
        c.centeredText(text, c.width() / 2, y, a << 24 | WHITE, true);
    }

    /**
     * The tool wheel: inventory-style slots around the crosshair, like the game mode switcher. {@code hovered} is the
     * slot under the cursor (-1 for none), {@code current} the tool in the slot now; the centre names whichever applies.
     */
    public static void wheel(OsdCanvas c, List<Entry> entries, int hovered, int current, double open,
                             double cursorX, double cursorY, String emptyText, String footer) {
        int w = c.width(), h = c.height();
        int cx = w / 2, cy = h / 2;
        int a = Math.max(4, (int) (open * 255));
        c.fill(0, 0, w, h, (int) (open * 0x50) << 24);

        int n = entries.size();
        double r = ToolWheel.RADIUS * (0.85 + 0.15 * open);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            double ang = Math.toRadians(ToolWheel.angleOf(i, n));
            int bx = cx + (int) Math.round(Math.sin(ang) * r);
            int by = cy - (int) Math.round(Math.cos(ang) * r);
            c.sprite(WHEEL_SLOT, bx - 13, by - 13, 26, 26);
            c.icon(e.icon(), bx - 8, by - 8);
            if (i == hovered && !e.locked()) c.sprite(SLOT_SELECTED, bx - 14, by - 14, 28, 28);
            if (i == current) c.fill(bx - 2, by + 15, bx + 2, by + 16, a << 24 | WHITE);
        }

        Entry shown = hovered >= 0 && hovered < n ? entries.get(hovered) : current >= 0 && current < n ? entries.get(current) : null;
        if (shown != null) {
            c.centeredText(shown.name(), cx, cy - 10, a << 24 | WHITE, true);
            if (shown.status() != null && !shown.status().isEmpty()) {
                c.centeredText(shown.status(), cx, cy + 1, a << 24 | YELLOW, true);
            }
            List<String> lines = wrap(c, shown.description(), Math.min(220, w - 20));
            int ty = cy - (int) r - 18 - lines.size() * (c.lineHeight() + 1);
            for (String line : lines) {
                c.centeredText(line, cx, ty, a << 24 | GRAY, true);
                ty += c.lineHeight() + 1;
            }
        } else if (n == 0) {
            c.centeredText(emptyText, cx, cy - 4, a << 24 | WHITE, true);
        }
        if (footer != null && !footer.isEmpty()) {
            c.centeredText(footer, cx, cy + (int) r + 20, a << 24 | GRAY, true);
        }

        int px = cx + (int) Math.round(cursorX), py = cy + (int) Math.round(cursorY);
        c.fill(px - 1, py, px + 2, py + 1, a << 24 | WHITE);
        c.fill(px, py - 1, px + 1, py + 2, a << 24 | WHITE);
    }

    static List<String> wrap(OsdCanvas c, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (c.textWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
