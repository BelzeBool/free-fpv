package com.belzebool.freefpv.core.osd;

import com.belzebool.freefpv.core.DronePhysics;

import java.util.Locale;
import java.util.Random;

/**
 * Draws the drone view overlay: a DJI Fly-style HUD for camera drones and a Betaflight-style OSD for FPV quads,
 * plus analog video artefacts. Pure layout code on top of {@link OsdCanvas}.
 */
public final class OsdPainter {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xB0FFFFFF;
    private static final int SHADE = 0x66000000;
    private static final int GREEN = 0xFF3DDC84;
    private static final int YELLOW = 0xFFFFC53D;
    private static final int RED = 0xFFFF4D4F;

    private final Random random = new Random();

    public void paint(OsdCanvas c, OsdState s) {
        if (s.showOsd || s.crashed) {
            if (s.mode.isFpv()) {
                if (s.analogEffects) analog(c, s);
                fpv(c, s);
            } else {
                camera(c, s);
            }
        }
        if (s.crashed) crashOverlay(c, s);
        if (s.helpAlpha > 0.01 && !s.help.isEmpty()) help(c, s);
        if (s.transitions && s.feedAge < 0.9) transition(c, s);
        if (s.hintTimer > 0 && !s.hint.isEmpty()) {
            // Like the vanilla action bar: plain text with a shadow, fading out.
            int alpha = Math.max(4, (int) (Math.min(1, s.hintTimer) * 255)) << 24;
            c.centeredText(s.hint, c.width() / 2, c.height() - 58, 0xFFFFFF | alpha, true);
        }
    }

    // ------------------------------------------------------------------ camera drone

    private void camera(OsdCanvas c, OsdState s) {
        int w = c.width(), h = c.height();
        c.fill(0, 0, w, 19, 0x70000000);
        c.fill(0, 19, w, 22, 0x30000000);

        // Mode chip
        int mw = c.textWidth(s.mode.displayName) + 22;
        pill(c, 4, 3, 4 + mw, 16, 0x40FFFFFF);
        pill(c, 5, 4, 17, 15, 0xEEFFFFFF);
        c.centeredText(s.mode.osdLabel, 11, 5, 0xFF111111, false);
        c.text(s.mode.displayName, 20, 5, WHITE, true);

        String status;
        int statusColor;
        if (s.crashed) {
            status = "Aircraft Disconnected";
            statusColor = RED;
        } else if (s.autopilot != DronePhysics.Autopilot.NONE) {
            status = switch (s.autopilot) {
                case RTH_DESCEND -> "Landing";
                case LANDED -> "Landed";
                default -> "Returning to Home";
            };
            statusColor = YELLOW;
        } else if (s.signal < 0.35) {
            status = "Weak Signal";
            statusColor = YELLOW;
        } else {
            status = "In Flight (GPS)";
            statusColor = GREEN;
        }
        int sw = c.textWidth(status) + 14;
        pill(c, w / 2 - sw / 2, 3, w / 2 + sw / 2, 16, (statusColor & 0x00FFFFFF) | 0xDD000000);
        c.centeredText(status, w / 2, 5, 0xFF0B0B0B, false);

        // Right cluster: GPS, remote link, video link, battery
        int right = w - 5;
        if (s.batteryEnabled) {
            int pct = (int) Math.round(s.battery * 100);
            int col = pct <= 10 ? RED : pct <= 25 ? YELLOW : GREEN;
            String left = time(s.battery * s.batteryMinutes * 60);
            c.rightText(left, right, 5, DIM, true);
            right -= c.textWidth(left) + 5;
            String label = pct + "%";
            c.rightText(label, right, 5, col, true);
            right -= c.textWidth(label) + 3;
            batteryIcon(c, right - 15, 5, s.battery, col);
            right -= 22;
        }
        right = bars(c, right, 14, s.signal, "HD");
        right = bars(c, right, 14, Math.min(1, s.signal * 1.1), null);
        remoteIcon(c, right - 9, 5);
        right -= 14;
        int satellites = 12 + (int) Math.round(s.signal * 8);
        c.rightText(String.valueOf(satellites), right, 5, WHITE, true);
        right -= c.textWidth(String.valueOf(satellites)) + 2;
        satelliteIcon(c, right - 9, 5);

        // Recording
        if (s.recording) {
            boolean on = ((int) (s.time * 2)) % 2 == 0;
            String rec = time(s.flightTime);
            int rw = c.textWidth(rec) + 16;
            pill(c, w / 2 - rw / 2, 24, w / 2 + rw / 2, 36, 0x99000000);
            if (on) disc(c, w / 2 - rw / 2 + 7, 30, 3, RED);
            c.text(rec, w / 2 - rw / 2 + 13, 26, WHITE, true);
        }

        // Home distance while returning
        if (s.autopilot == DronePhysics.Autopilot.RTH_CLIMB || s.autopilot == DronePhysics.Autopilot.RTH_CRUISE) {
            String rth = String.format(Locale.ROOT, "H  %.0f m", s.distancePilot);
            int rw = c.textWidth(rth) + 12;
            pill(c, w / 2 - rw / 2, 40, w / 2 + rw / 2, 52, 0xBB1C1C1C);
            c.centeredText(rth, w / 2, 42, YELLOW, true);
        }

        // Gimbal scale and shutter button, right edge
        int gx = w - 12, gTop = h / 2 - 48, gBottom = h / 2 + 22;
        c.fill(gx, gTop, gx + 2, gBottom, 0x66FFFFFF);
        double t = (s.gimbal - 20) / (-90 - 20);
        int gy = gTop + (int) Math.round(t * (gBottom - gTop));
        int zeroY = gTop + (int) Math.round((0 - 20) / (-110.0) * (gBottom - gTop));
        c.fill(gx - 3, zeroY, gx + 5, zeroY + 1, DIM);
        pill(c, gx - 4, gy - 2, gx + 6, gy + 3, WHITE);
        c.rightText(String.format(Locale.ROOT, "%.0f°", s.gimbal), gx - 6, gy - 4, WHITE, true);
        int bx = w - 17, by = h / 2 + 42;
        ring(c, bx, by, 10, 0xDDFFFFFF);
        disc(c, bx, by, 7, s.recording ? RED : 0xEEFFFFFF);
        if (s.recording) c.fill(bx - 2, by - 2, bx + 3, by + 3, 0xFFFFFFFF);

        // Radar: heading-up map with the home point, bottom left
        int rx = 30, ry = h - 32, rr = 24;
        disc(c, rx, ry, rr, 0x66000000);
        ring(c, rx, ry, rr, 0x99FFFFFF);
        ring(c, rx, ry, rr / 2, 0x33FFFFFF);
        double north = Math.toRadians(-s.compass);
        c.centeredText("N", rx + (int) Math.round(Math.sin(north) * (rr - 6)), ry - (int) Math.round(Math.cos(north) * (rr - 6)) - 4, DIM, true);
        if (s.distancePilot > 1) {
            double scale = Math.min(1, Math.log1p(s.distancePilot) / Math.log1p(Math.max(20, s.maxRange)));
            double b = Math.toRadians(s.homeBearing);
            int hx = rx + (int) Math.round(Math.sin(b) * (rr - 4) * scale);
            int hy = ry - (int) Math.round(Math.cos(b) * (rr - 4) * scale);
            disc(c, hx, hy, 3, YELLOW);
            c.text("H", hx - 2, hy - 3, 0xFF111111, false);
        }
        c.push();
        c.translate(rx, ry);
        arrowGlyph(c, 0xFFFFFFFF);
        c.pop();
        c.centeredText(String.format(Locale.ROOT, "%03.0f°", s.compass), rx, ry - rr - 10, WHITE, true);

        // Telemetry next to the radar
        int tx = rx + rr + 8, ty = h - 30;
        c.fill(tx - 4, ty - 4, tx + 132, h - 5, 0x55000000);
        labelValue(c, tx, ty, "H", String.format(Locale.ROOT, "%.1f m", s.altitude));
        labelValue(c, tx + 66, ty, "D", String.format(Locale.ROOT, "%.0f m", s.distanceHome));
        labelValue(c, tx, ty + 12, "H.S", String.format(Locale.ROOT, "%.1f m/s", s.horizontalSpeed));
        labelValue(c, tx + 66, ty + 12, "V.S", String.format(Locale.ROOT, "%.1f m/s", s.verticalSpeed));

        // Camera settings and zoom, bottom right
        String settings = String.format(Locale.ROOT, "4K 30   ISO 100   1/%d   EV 0", s.zoom > 2 ? 500 : 250);
        c.rightText(settings, w - 6, h - 12, DIM, true);
        if (s.zoom > 1.01) {
            String zoom = String.format(Locale.ROOT, "%.1fx", s.zoom);
            int zw = c.textWidth(zoom) + 10;
            pill(c, w - 6 - zw, h - 27, w - 6, h - 15, 0xAA000000);
            c.centeredText(zoom, w - 6 - zw / 2, h - 25, YELLOW, true);
        }

        // Warnings
        String warning = null;
        if (s.batteryEnabled && s.battery < 0.05) warning = "Critical battery. Landing";
        else if (s.batteryEnabled && s.battery < 0.2) warning = "Low battery";
        else if (s.distancePilot > s.maxRange) warning = "Max distance reached";
        else if (s.signal < 0.35) warning = "Weak signal. Fly closer";
        if (warning != null && !s.crashed) {
            int ww = c.textWidth(warning) + 16;
            int wy = s.recording ? 40 : 26;
            pill(c, w / 2 - ww / 2, wy - 3, w / 2 + ww / 2, wy + 11, 0xDD8A1E1E);
            c.centeredText(warning, w / 2, wy, WHITE, true);
        }
    }

    private static void labelValue(OsdCanvas c, int x, int y, String label, String value) {
        c.text(label, x, y, 0xFFA8B0B8, true);
        c.text(value, x + c.textWidth(label) + 4, y, WHITE, true);
    }

    /** Signal bars growing right to left from {@code right}; returns the x left of what was drawn. */
    private static int bars(OsdCanvas c, int right, int baseline, double level, String tag) {
        int x = right;
        if (tag != null) {
            c.rightText(tag, x, 5, DIM, true);
            x -= c.textWidth(tag) + 2;
        }
        int count = (int) Math.ceil(level * 4.0 - 0.01);
        for (int i = 3; i >= 0; i--) {
            int bh = 2 + i * 2;
            x -= 3;
            c.fill(x, baseline - bh, x + 2, baseline, i < count ? WHITE : 0x55FFFFFF);
            x -= 1;
        }
        return x - 5;
    }

    private static void batteryIcon(OsdCanvas c, int x, int y, double level, int color) {
        c.fill(x, y, x + 14, y + 8, 0xCCFFFFFF);
        c.fill(x + 1, y + 1, x + 13, y + 7, 0xFF1A1A1A);
        c.fill(x + 1, y + 1, x + 1 + (int) Math.ceil(12 * Math.max(0, Math.min(1, level))), y + 7, color);
        c.fill(x + 14, y + 2, x + 15, y + 6, 0xCCFFFFFF);
    }

    /** Tiny remote controller: two sticks on a bar. */
    private static void remoteIcon(OsdCanvas c, int x, int y) {
        c.fill(x, y + 3, x + 9, y + 7, WHITE);
        c.fill(x + 1, y + 1, x + 2, y + 3, WHITE);
        c.fill(x + 7, y + 1, x + 8, y + 3, WHITE);
        c.fill(x + 2, y + 7, x + 3, y + 8, WHITE);
        c.fill(x + 6, y + 7, x + 7, y + 8, WHITE);
    }

    /** Tiny satellite: a body with two panels. */
    private static void satelliteIcon(OsdCanvas c, int x, int y) {
        c.fill(x + 3, y + 2, x + 6, y + 6, WHITE);
        c.fill(x, y + 3, x + 3, y + 5, 0xFFB8C8FF);
        c.fill(x + 6, y + 3, x + 9, y + 5, 0xFFB8C8FF);
        c.fill(x + 4, y, x + 5, y + 2, WHITE);
    }

    /** Aircraft arrow pointing up, drawn around the current origin. */
    private static void arrowGlyph(OsdCanvas c, int color) {
        for (int row = 0; row < 7; row++) {
            int half = row / 2;
            c.fill(-half, -4 + row, half + 1, -3 + row, color);
        }
        c.fill(-3, 2, -1, 3, 0x00000000);
    }

    /** Rounded rectangle with a two-pixel corner radius. */
    private static void pill(OsdCanvas c, int x0, int y0, int x1, int y1, int color) {
        if (y1 - y0 < 4 || x1 - x0 < 4) {
            c.fill(x0, y0, x1, y1, color);
            return;
        }
        c.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        c.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
        c.fill(x0, y0 + 2, x1, y1 - 2, color);
        c.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
        c.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
    }

    private static void disc(OsdCanvas c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void ring(OsdCanvas c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            int inner = Math.abs(dy) < r - 1 ? (int) Math.floor(Math.sqrt((r - 1) * (r - 1) - dy * dy + 0.5)) : -1;
            if (inner < 0) {
                c.fill(cx - outer, cy + dy, cx + outer + 1, cy + dy + 1, color);
            } else {
                c.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                c.fill(cx + inner + 1, cy + dy, cx + outer + 1, cy + dy + 1, color);
            }
        }
    }

    // ------------------------------------------------------------------ FPV

    /** Betaflight's OSD font is white with a black outline so it reads on sky and snow alike. */
    private static void osdText(OsdCanvas c, String text, int x, int y, int color) {
        int a = color & 0xFF000000;
        int outline = a == 0 ? 0xFF000000 : (int) (((a >>> 24) * 0.85)) << 24;
        c.text(text, x - 1, y, outline, false);
        c.text(text, x + 1, y, outline, false);
        c.text(text, x, y - 1, outline, false);
        c.text(text, x, y + 1, outline, false);
        c.text(text, x, y, color, false);
    }

    private static void osdCentered(OsdCanvas c, String text, int cx, int y, int color) {
        osdText(c, text, cx - c.textWidth(text) / 2, y, color);
    }

    private static void osdRight(OsdCanvas c, String text, int right, int y, int color) {
        osdText(c, text, right - c.textWidth(text), y, color);
    }

    /** Filled rectangle with a one-pixel black outline, the building block of the OSD glyphs. */
    private static void block(OsdCanvas c, int x0, int y0, int x1, int y1, int color) {
        c.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xD0000000);
        c.fill(x0, y0, x1, y1, color);
    }

    private void fpv(OsdCanvas c, OsdState s) {
        int w = c.width(), h = c.height();
        int cx = w / 2, cy = h / 2;

        // Artificial horizon
        double pxPerDeg = h / Math.max(20, s.verticalFov);
        c.push();
        c.translate(cx, cy);
        c.rotate((float) Math.toRadians(-s.roll));
        int offset = (int) Math.round(s.pitch * pxPerDeg);
        offset = Math.max(-h / 3, Math.min(h / 3, offset));
        for (int i = -5; i <= 5; i++) {
            if (Math.abs(i) < 1) continue;
            int x = i * 9;
            block(c, x - 3, offset, x + 3, offset + 1, 0xE0FFFFFF);
        }
        c.pop();

        // Horizon sidebars
        for (int i = -2; i <= 2; i++) {
            int y = cy + i * 12;
            block(c, cx - 64, y, cx - 59, y + 1, i == 0 ? WHITE : DIM);
            block(c, cx + 59, y, cx + 64, y + 1, i == 0 ? WHITE : DIM);
        }

        // Crosshair
        block(c, cx - 9, cy, cx - 3, cy + 1, WHITE);
        block(c, cx + 3, cy, cx + 9, cy + 1, WHITE);
        block(c, cx - 1, cy - 2, cx + 1, cy - 1, WHITE);

        // Link quality and RSSI with an antenna glyph
        int linkColor = s.signal < 0.35 ? RED : WHITE;
        antennaGlyph(c, 6, 5, linkColor);
        osdText(c, String.format(Locale.ROOT, "LQ %d", (int) Math.round(s.signal * 100)), 16, 6, linkColor);
        osdText(c, String.format(Locale.ROOT, "RSSI -%d", (int) Math.round(40 + (1 - s.signal) * 65)), 16, 17, linkColor);

        // Throttle
        osdRight(c, String.format(Locale.ROOT, "THR %d", (int) Math.round(s.throttle * 100)), w - 6, 6, WHITE);
        throttleBar(c, s, w - 9, 18, 48);

        // Home: an arrow pointing at the pilot, and the distance
        String home = String.format(Locale.ROOT, "%.0fm", s.distancePilot);
        int hw = c.textWidth(home) + 12;
        c.push();
        c.translate(cx - hw / 2 + 4, 10);
        c.rotate((float) Math.toRadians(s.homeBearing));
        homeArrow(c);
        c.pop();
        osdText(c, home, cx - hw / 2 + 11, 6, WHITE);

        // Bottom row: battery, flight mode and timer, altitude and speed
        if (s.batteryEnabled) {
            int col = s.voltage < 13.6 ? RED : s.voltage < 14.4 ? YELLOW : WHITE;
            batteryGlyph(c, 6, h - 23, s.battery, col);
            osdText(c, String.format(Locale.ROOT, "%.1fV", s.voltage), 14, h - 22, col);
            osdText(c, String.format(Locale.ROOT, "%d%%", (int) Math.round(s.battery * 100)), 14, h - 12, DIM);
        }
        osdCentered(c, s.mode.osdLabel, cx, h - 22, WHITE);
        if (s.altitudeHold) osdCentered(c, "ALT HOLD", cx, h - 32, DIM);
        String timer = time(s.flightTime);
        int tw = c.textWidth(timer);
        clockGlyph(c, cx - tw / 2 - 9, h - 12);
        osdText(c, timer, cx - tw / 2, h - 12, WHITE);
        String alt = String.format(Locale.ROOT, "%.1fm", s.altitude);
        osdRight(c, alt, w - 6, h - 22, WHITE);
        altitudeGlyph(c, w - 6 - c.textWidth(alt) - 7, h - 23);
        osdRight(c, String.format(Locale.ROOT, "%d km/h", (int) Math.round(Math.hypot(s.horizontalSpeed, s.verticalSpeed) * 3.6)), w - 6, h - 12, WHITE);

        // Warnings
        String warning = null;
        if (s.batteryEnabled && s.voltage < 13.6) warning = "LAND NOW";
        else if (s.signal < 0.35) warning = "RSSI LOW";
        if (warning != null && !s.crashed && ((int) (s.time * 3)) % 2 == 0) {
            osdCentered(c, warning, cx, cy - 34, WHITE);
        }
    }

    private static void antennaGlyph(OsdCanvas c, int x, int y, int color) {
        block(c, x + 3, y + 2, x + 4, y + 9, color);
        block(c, x, y, x + 1, y + 3, color);
        block(c, x + 6, y, x + 7, y + 3, color);
        c.fill(x + 1, y + 2, x + 3, y + 3, color);
        c.fill(x + 4, y + 2, x + 6, y + 3, color);
    }

    private static void batteryGlyph(OsdCanvas c, int x, int y, double level, int color) {
        block(c, x + 1, y, x + 4, y + 1, color);
        c.fill(x - 1, y + 1, x + 6, y + 10, 0xD0000000);
        c.fill(x, y + 1, x + 5, y + 9, color);
        c.fill(x + 1, y + 2, x + 4, y + 8, 0xFF000000);
        int fill = (int) Math.round(6 * Math.max(0, Math.min(1, level)));
        c.fill(x + 1, y + 8 - fill, x + 4, y + 8, color);
    }

    private static void clockGlyph(OsdCanvas c, int x, int y) {
        c.fill(x - 1, y - 1, x + 8, y + 8, 0xD0000000);
        c.fill(x, y, x + 7, y + 7, WHITE);
        c.fill(x + 1, y + 1, x + 6, y + 6, 0xFF000000);
        c.fill(x + 3, y + 1, x + 4, y + 4, WHITE);
        c.fill(x + 3, y + 3, x + 5, y + 4, WHITE);
    }

    private static void altitudeGlyph(OsdCanvas c, int x, int y) {
        block(c, x + 2, y + 1, x + 3, y + 8, WHITE);
        c.fill(x + 1, y + 2, x + 4, y + 3, WHITE);
        c.fill(x + 1, y + 6, x + 4, y + 7, WHITE);
    }

    /** Arrow pointing up (at the pilot after the caller's rotation), outlined. */
    private static void homeArrow(OsdCanvas c) {
        for (int row = 0; row < 5; row++) c.fill(-row - 1, -4 + row, row + 2, -3 + row + 1, 0xD0000000);
        c.fill(-1, 1, 2, 5, 0xD0000000);
        for (int row = 0; row < 4; row++) c.fill(-row, -3 + row, row + 1, -2 + row, WHITE);
        c.fill(0, 1, 1, 4, WHITE);
    }

    /** Thin vertical throttle gauge with a tick where the quad hovers; helps keyboard pilots find hover. */
    private void throttleBar(OsdCanvas c, OsdState s, int x, int top, int height) {
        int bottom = top + height;
        c.fill(x, top, x + 3, bottom, 0x55000000);
        int filled = (int) Math.round(Math.max(0, Math.min(1, s.throttle)) * height);
        c.fill(x, bottom - filled, x + 3, bottom, s.throttle > 0.95 ? YELLOW : DIM);
        int hy = bottom - (int) Math.round(Math.max(0, Math.min(1, s.hoverThrottle)) * height);
        c.fill(x - 2, hy, x + 5, hy + 1, GREEN);
    }

    /** Controls panel on the left, drawn like a vanilla tooltip: keys in yellow, what they do in white. */
    private void help(OsdCanvas c, OsdState s) {
        int alpha = (int) (Math.min(1, s.helpAlpha) * 255);
        if (alpha < 4) return;
        int line = c.lineHeight() + 2;
        int keyW = 0, actionW = 0;
        for (String[] row : s.help) {
            keyW = Math.max(keyW, c.textWidth(row[0]));
            actionW = Math.max(actionW, c.textWidth(row[1]));
        }
        int pad = 4;
        int width = Math.max(c.textWidth(s.helpTitle), keyW + 8 + actionW) + pad * 2;
        int height = (s.help.size() + 1) * line + pad * 2 + 2;
        int x = 8, y = Math.max(22, Math.min(c.height() / 2 - height / 2, c.height() - 72 - height));
        int bg = (int) (alpha * 0.94) << 24 | 0x100010;
        c.fill(x + 1, y, x + width - 1, y + height, bg);
        c.fill(x, y + 1, x + width, y + height - 1, bg);
        int top = (int) (alpha * 0.31) << 24 | 0x5000FF, bottom = (int) (alpha * 0.31) << 24 | 0x28007F;
        c.hLine(x + 1, x + width - 2, y + 1, top);
        c.hLine(x + 1, x + width - 2, y + height - 2, bottom);
        c.vLine(x + 1, y + 1, y + height / 2, top);
        c.vLine(x + width - 2, y + 1, y + height / 2, top);
        c.vLine(x + 1, y + height / 2, y + height - 2, bottom);
        c.vLine(x + width - 2, y + height / 2, y + height - 2, bottom);
        c.text(s.helpTitle, x + pad, y + pad, alpha << 24 | 0xFFFFFF, true);
        int ry = y + pad + line + 2;
        for (String[] row : s.help) {
            c.text(row[0], x + pad, ry, alpha << 24 | 0xFFFF55, true);
            c.text(row[1], x + pad + keyW + 8, ry, alpha << 24 | 0xE0E0E0, true);
            ry += line;
        }
    }

    /** Video feed coming up: DJI-style fade from black, or analog snow clearing on FPV goggles. */
    private void transition(OsdCanvas c, OsdState s) {
        int w = c.width(), h = c.height();
        double t = s.feedAge;
        if (s.mode.isFpv()) {
            double k = Math.max(0, 1 - t / 0.7);
            if (k > 0) noise(c, k * 1.8);
            int black = (int) (Math.max(0, 1 - t / 0.35) * 255);
            if (black > 0) c.fill(0, 0, w, h, black << 24);
        } else {
            double k = Math.max(0, 1 - t / 0.75);
            int black = (int) (Math.pow(k, 1.5) * 255);
            if (black > 0) c.fill(0, 0, w, h, black << 24);
            if (t < 0.5) {
                int dots = (int) (t * 8) % 4;
                c.centeredText(s.connectingText + ".".repeat(dots), w / 2, h / 2 - 4, 0xFFFFFF | (int) (Math.min(1, k * 1.5) * 255) << 24, true);
            }
        }
    }

    private void analog(OsdCanvas c, OsdState s) {
        int w = c.width(), h = c.height();
        for (int y = 0; y < h; y += 3) c.fill(0, y, w, y + 1, 0x12000000);
        // Soft vignette
        for (int i = 0; i < 6; i++) {
            int a = (6 - i) * 7 << 24;
            int m = i * 4;
            c.fill(0, m, w, m + 4, a);
            c.fill(0, h - m - 4, w, h - m, a);
            c.fill(m, 0, m + 4, h, a);
            c.fill(w - m - 4, 0, w - m, h, a);
        }
        double breakup = Math.max(0, 1 - s.signal / 0.6);
        if (breakup > 0) noise(c, breakup);
    }

    private void noise(OsdCanvas c, double amount) {
        int w = c.width(), h = c.height();
        int count = (int) (amount * 900);
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(w), y = random.nextInt(h);
            int gray = 120 + random.nextInt(136);
            int alpha = (int) (Math.min(1, amount) * 200);
            c.fill(x, y, x + 1 + random.nextInt(3), y + 1, alpha << 24 | gray << 16 | gray << 8 | gray);
        }
        int bands = (int) (amount * 6);
        for (int i = 0; i < bands; i++) {
            int y = random.nextInt(h);
            c.fill(0, y, w, y + 1 + random.nextInt(2), (int) (amount * 90) << 24 | 0xFFFFFF);
        }
    }

    private void crashOverlay(OsdCanvas c, OsdState s) {
        int w = c.width(), h = c.height();
        double k = Math.min(1, s.crashTimer / 0.6);
        if (s.mode.isFpv()) {
            noise(c, 0.4 + k * 1.6);
            c.fill(0, 0, w, h, (int) (k * 120) << 24);
        } else {
            c.fill(0, 0, w, h, (int) (k * 150) << 24);
        }
        String title = switch (s.crashReason) {
            case WATER -> s.mode.isFpv() ? "FAILSAFE - WATER" : "Aircraft in water";
            case SIGNAL_LOST -> s.mode.isFpv() ? "FAILSAFE - RXLOSS" : "Signal lost";
            case BATTERY -> s.mode.isFpv() ? "BATTERY DEAD" : "Battery depleted";
            default -> s.mode.isFpv() ? "CRASH" : "Aircraft disconnected";
        };
        c.push();
        c.translate(w / 2f, h / 2f - 10);
        c.scale(2);
        c.centeredText(title, 0, 0, s.mode.isFpv() ? WHITE : RED, true);
        c.pop();
    }

    private static String time(double seconds) {
        int total = (int) Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }
}
