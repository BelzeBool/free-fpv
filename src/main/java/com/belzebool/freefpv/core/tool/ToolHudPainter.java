package com.belzebool.freefpv.core.tool;

import com.belzebool.freefpv.core.osd.OsdCanvas;

import java.util.ArrayList;
import java.util.List;

/** Draws the 10th tool slot next to the hotbar and the radial tool wheel. Pure layout on top of {@link OsdCanvas}. */
public final class ToolHudPainter {
    public static final String SLOT = "freefpv:tool/slot";
    public static final String SLOT_SELECTED = "freefpv:tool/slot_selected";
    public static final String WHEEL = "freefpv:tool/wheel";
    public static final String BUBBLE = "freefpv:tool/bubble";
    public static final String BUBBLE_HOVER = "freefpv:tool/bubble_hover";
    public static final String BUBBLE_LOCKED = "freefpv:tool/bubble_locked";
    public static final String CENTER = "freefpv:tool/center";

    /** One tool as the wheel shows it. {@code status} is a short line such as the current flight mode. */
    public record Entry(String name, String description, String icon, String status, boolean locked) {
    }

    private ToolHudPainter() {
    }

    /** The tool slot: 24x24 with the tool icon, a mode badge and the selection frame when active. */
    public static void slot(OsdCanvas c, int x, int y, String icon, boolean active, String badge, String keyHint) {
        c.sprite(SLOT, x, y, 24, 24);
        if (icon != null) c.icon(icon, x + 4, y + 4);
        if (badge != null && !badge.isEmpty()) {
            c.push();
            c.translate(x + 2, y + 2);
            c.scale(0.5f);
            c.fill(-1, -1, c.textWidth(badge) + 1, c.lineHeight() - 1, 0xC0101216);
            c.text(badge, 0, 0, 0xFFFFC53D, false);
            c.pop();
        }
        if (active) {
            c.sprite(SLOT_SELECTED, x - 1, y - 1, 26, 26);
        } else if (keyHint != null && !keyHint.isEmpty()) {
            c.push();
            c.translate(x + 12, y - 5);
            c.scale(0.5f);
            c.centeredText(keyHint, 0, 0, 0xB0FFFFFF, true);
            c.pop();
        }
    }

    /** Name of the tool just picked, fading out above the hotbar like the vanilla held-item name. */
    public static void toolName(OsdCanvas c, String text, double alpha, int y) {
        if (alpha <= 0.02 || text == null || text.isEmpty()) return;
        int a = (int) (Math.min(1, alpha) * 255);
        int w = c.textWidth(text) + 8;
        c.fill(c.width() / 2 - w / 2, y - 2, c.width() / 2 + w / 2, y + c.lineHeight(), (int) (a * 0.45) << 24);
        c.centeredText(text, c.width() / 2, y, a << 24 | 0xFFFFFF, true);
    }

    /**
     * The radial wheel. {@code hovered} is the slot under the cursor (-1 for none), {@code current} the tool in the
     * slot now; the centre shows whichever of the two applies.
     */
    public static void wheel(OsdCanvas c, List<Entry> entries, int hovered, int current, double open,
                             double cursorX, double cursorY, String emptyText, String footer) {
        int w = c.width(), h = c.height();
        int cx = w / 2, cy = h / 2;
        int a = (int) (open * 255);
        c.fill(0, 0, w, h, (int) (open * 0x70) << 24);

        float grow = (float) (0.82 + 0.18 * open);
        int ring = (int) ((ToolWheel.RADIUS + 26) * 2 * grow);
        c.sprite(WHEEL, cx - ring / 2, cy - ring / 2, ring, ring);
        c.sprite(CENTER, cx - 36, cy - 36, 72, 72);

        int n = entries.size();
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            double ang = Math.toRadians(ToolWheel.angleOf(i, n));
            double r = ToolWheel.RADIUS * grow;
            int bx = cx + (int) Math.round(Math.sin(ang) * r);
            int by = cy - (int) Math.round(Math.cos(ang) * r);
            boolean hot = i == hovered && !e.locked();
            int size = hot ? 38 : 32;
            c.sprite(e.locked() ? BUBBLE_LOCKED : hot ? BUBBLE_HOVER : BUBBLE, bx - size / 2, by - size / 2, size, size);
            c.push();
            c.translate(bx, by);
            c.scale(hot ? 1.6f : 1.3f);
            c.icon(e.icon(), -8, -8);
            c.pop();
            if (i == current) c.fill(bx - 3, by + size / 2 + 2, bx + 3, by + size / 2 + 4, a << 24 | 0xFF7A1A);
        }

        Entry shown = hovered >= 0 && hovered < n ? entries.get(hovered) : current >= 0 && current < n ? entries.get(current) : null;
        if (shown != null) {
            c.centeredText(shown.name(), cx, cy - 12, a << 24 | 0xFFFFFF, true);
            if (shown.status() != null && !shown.status().isEmpty()) {
                c.centeredText(shown.status(), cx, cy - 1, a << 24 | 0xFFC53D, true);
            }
            List<String> lines = wrap(c, shown.description(), Math.min(220, w - 20));
            int ty = cy - ring / 2 - 4 - lines.size() * (c.lineHeight() + 1);
            for (String line : lines) {
                c.centeredText(line, cx, ty, (int) (a * 0.85) << 24 | 0xE0E0E0, true);
                ty += c.lineHeight() + 1;
            }
        } else if (n == 0) {
            c.centeredText(emptyText, cx, cy - 4, a << 24 | 0xFFFFFF, true);
        }
        if (footer != null && !footer.isEmpty()) {
            c.centeredText(footer, cx, cy + ring / 2 + 3, (int) (a * 0.7) << 24 | 0xFFFFFF, true);
        }

        int px = cx + (int) Math.round(cursorX), py = cy + (int) Math.round(cursorY);
        c.fill(px - 2, py - 1, px + 3, py + 2, a << 24 | 0xFFFFFF);
        c.fill(px - 1, py - 2, px + 2, py + 3, a << 24 | 0xFFFFFF);
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
