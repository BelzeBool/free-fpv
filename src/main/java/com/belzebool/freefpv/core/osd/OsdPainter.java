package com.belzebool.freefpv.core.osd;

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
        if (s.mode.isFpv()) {
            if (s.analogEffects) analog(c, s);
            fpv(c, s);
        } else {
            camera(c, s);
        }
        if (s.crashed) crashOverlay(c, s);
        if (s.hintTimer > 0 && !s.hint.isEmpty()) {
            int alpha = (int) (Math.min(1, s.hintTimer) * 255) << 24;
            int y = c.height() - 58;
            int w = c.textWidth(s.hint) + 10;
            c.fill(c.width() / 2 - w / 2, y - 3, c.width() / 2 + w / 2, y + c.lineHeight() + 1, (int) (Math.min(1, s.hintTimer) * 0x80) << 24);
            c.centeredText(s.hint, c.width() / 2, y, 0xFFFFFF | alpha, true);
        }
    }

    // ------------------------------------------------------------------ camera drone

    private void camera(OsdCanvas c, OsdState s) {
        int w = c.width(), h = c.height();
        c.fill(0, 0, w, 18, SHADE);

        // Mode badge + status
        String badge = s.mode.osdLabel;
        c.fill(4, 3, 16, 15, 0xCCFFFFFF);
        c.centeredText(badge, 10, 5, 0xFF111111, false);
        c.text(s.mode.displayName, 20, 5, WHITE, true);

        String status = s.crashed ? "Aircraft Disconnected" : s.signal < 0.35 ? "Weak Signal" : "In Flight (GPS)";
        int statusColor = s.crashed ? RED : s.signal < 0.35 ? YELLOW : GREEN;
        int sw = c.textWidth(status) + 12;
        c.fill(w / 2 - sw / 2, 2, w / 2 + sw / 2, 16, (statusColor & 0x00FFFFFF) | 0xCC000000);
        c.centeredText(status, w / 2, 5, 0xFF0B0B0B, false);

        // Signal bars
        int bars = (int) Math.ceil(s.signal * 4.0 - 0.01);
        int bx = w / 2 + sw / 2 + 8;
        for (int i = 0; i < 4; i++) {
            int bh = 3 + i * 2;
            c.fill(bx + i * 4, 14 - bh, bx + i * 4 + 3, 14, i < bars ? WHITE : 0x55FFFFFF);
        }
        c.text("HD", bx + 18, 5, DIM, true);

        // Battery, top right
        if (s.batteryEnabled) {
            int pct = (int) Math.round(s.battery * 100);
            int col = pct <= 10 ? RED : pct <= 25 ? YELLOW : GREEN;
            String left = time(s.battery * s.batteryMinutes * 60);
            String label = pct + "%";
            int right = w - 6;
            c.rightText(left, right, 5, DIM, true);
            right -= c.textWidth(left) + 6;
            c.rightText(label, right, 5, col, true);
            right -= c.textWidth(label) + 4;
            c.fill(right - 14, 5, right, 13, 0xAAFFFFFF);
            c.fill(right - 13, 6, right - 1, 12, 0xFF222222);
            c.fill(right - 13, 6, right - 13 + (int) Math.ceil(12 * s.battery), 12, col);
            c.fill(right, 7, right + 1, 11, 0xAAFFFFFF);
        }

        // Recording
        if (s.recording) {
            boolean on = ((int) (s.time * 2)) % 2 == 0;
            if (on) c.fill(8, 24, 13, 29, RED);
            c.text("REC " + time(s.flightTime), 16, 23, WHITE, true);
        }

        // Telemetry, bottom left
        int ty = h - 30;
        c.fill(4, ty - 4, 150, h - 4, SHADE);
        c.text(String.format(Locale.ROOT, "H %.1f m", s.altitude), 8, ty, WHITE, true);
        c.text(String.format(Locale.ROOT, "D %.0f m", s.distanceHome), 78, ty, WHITE, true);
        c.text(String.format(Locale.ROOT, "H.S %.1f m/s", s.horizontalSpeed), 8, ty + 12, WHITE, true);
        c.text(String.format(Locale.ROOT, "V.S %.1f m/s", s.verticalSpeed), 78, ty + 12, WHITE, true);

        // Gimbal scale, right edge
        int gx = w - 14, gTop = h / 2 - 50, gBottom = h / 2 + 50;
        c.fill(gx, gTop, gx + 2, gBottom, 0x88FFFFFF);
        double t = (s.gimbal - 20) / (-90 - 20);
        int gy = gTop + (int) Math.round(t * (gBottom - gTop));
        int zeroY = gTop + (int) Math.round((0 - 20) / (-110.0) * (gBottom - gTop));
        c.fill(gx - 3, zeroY, gx + 5, zeroY + 1, DIM);
        c.fill(gx - 4, gy - 2, gx + 6, gy + 2, WHITE);
        c.rightText(String.format(Locale.ROOT, "%.0f°", s.gimbal), gx - 6, gy - 4, WHITE, true);

        // Zoom
        if (s.zoom > 1.01) {
            String zoom = String.format(Locale.ROOT, "%.1fx", s.zoom);
            c.rightText(zoom, w - 8, h - 18, WHITE, true);
        }

        // Compass tape, bottom centre
        compassTape(c, s, w / 2, h - 14, 140);

        // Warnings
        String warning = null;
        if (s.batteryEnabled && s.battery < 0.05) warning = "Critical battery. Landing";
        else if (s.batteryEnabled && s.battery < 0.2) warning = "Low battery";
        else if (s.distancePilot > s.maxRange) warning = "Max distance reached";
        else if (s.signal < 0.35) warning = "Weak signal. Fly closer";
        if (warning != null && !s.crashed) {
            int ww = c.textWidth(warning) + 12;
            int wy = 26;
            c.fill(w / 2 - ww / 2, wy - 3, w / 2 + ww / 2, wy + 11, 0xCC7A1F1F);
            c.centeredText(warning, w / 2, wy, WHITE, true);
        }
    }

    private void compassTape(OsdCanvas c, OsdState s, int cx, int y, int width) {
        double pxPerDeg = width / 120.0;
        int left = cx - width / 2, right = cx + width / 2;
        c.fill(left, y + 9, right, y + 10, 0x66FFFFFF);
        int start = (int) Math.floor((s.compass - 60) / 15) * 15;
        for (int deg = start; deg <= s.compass + 60; deg += 15) {
            int x = cx + (int) Math.round((deg - s.compass) * pxPerDeg);
            if (x < left || x > right) continue;
            int norm = ((deg % 360) + 360) % 360;
            String label = switch (norm) {
                case 0 -> "N";
                case 90 -> "E";
                case 180 -> "S";
                case 270 -> "W";
                default -> norm % 45 == 0 ? String.valueOf(norm) : null;
            };
            if (label != null) c.centeredText(label, x, y - 1, norm % 90 == 0 ? WHITE : DIM, true);
            else c.fill(x, y + 5, x + 1, y + 9, DIM);
        }
        c.fill(cx - 1, y + 7, cx + 1, y + 12, YELLOW);
    }

    // ------------------------------------------------------------------ FPV

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
            c.fill(x - 3, offset, x + 3, offset + 1, DIM);
        }
        c.pop();

        // Horizon sidebars
        for (int i = -2; i <= 2; i++) {
            int y = cy + i * 12;
            c.fill(cx - 62, y, cx - 58, y + 1, i == 0 ? WHITE : DIM);
            c.fill(cx + 58, y, cx + 62, y + 1, i == 0 ? WHITE : DIM);
        }

        // Crosshair
        c.fill(cx - 8, cy, cx - 3, cy + 1, WHITE);
        c.fill(cx + 3, cy, cx + 8, cy + 1, WHITE);
        c.fill(cx - 1, cy - 1, cx + 1, cy + 1, WHITE);

        // Top row: link quality, home arrow + distance, throttle
        int lq = (int) Math.round(s.signal * 100);
        c.text("LQ " + lq, 6, 6, s.signal < 0.35 ? RED : WHITE, true);
        c.text("RSSI -" + (int) Math.round(40 + (1 - s.signal) * 65), 6, 16, s.signal < 0.35 ? RED : WHITE, true);
        c.rightText(String.format(Locale.ROOT, "THR %d", (int) Math.round(s.throttle * 100)), w - 6, 6, WHITE, true);

        String home = arrow(s.homeBearing) + String.format(Locale.ROOT, " %.0fm", s.distancePilot);
        c.centeredText(home, cx, 6, WHITE, true);

        // Bottom row: voltage, mode + timer, altitude + speed
        if (s.batteryEnabled) {
            int col = s.voltage < 13.6 ? RED : s.voltage < 14.4 ? YELLOW : WHITE;
            c.text(String.format(Locale.ROOT, "%.1fV", s.voltage), 6, h - 22, col, true);
            c.text(String.format(Locale.ROOT, "%d%%", (int) Math.round(s.battery * 100)), 6, h - 12, DIM, true);
        }
        c.centeredText(s.mode.osdLabel, cx, h - 22, WHITE, true);
        c.centeredText(time(s.flightTime), cx, h - 12, WHITE, true);
        c.rightText(String.format(Locale.ROOT, "%.1fm", s.altitude), w - 6, h - 22, WHITE, true);
        c.rightText(String.format(Locale.ROOT, "%d km/h", (int) Math.round(Math.hypot(s.horizontalSpeed, s.verticalSpeed) * 3.6)), w - 6, h - 12, WHITE, true);

        // Warnings
        String warning = null;
        if (s.batteryEnabled && s.voltage < 13.6) warning = "LAND NOW";
        else if (s.signal < 0.35) warning = "RSSI LOW";
        if (warning != null && !s.crashed && ((int) (s.time * 3)) % 2 == 0) {
            c.centeredText(warning, cx, cy - 34, WHITE, true);
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

    private static String arrow(double bearing) {
        double b = ((bearing % 360) + 360) % 360;
        int sector = (int) Math.round(b / 45) % 8;
        return switch (sector) {
            case 0 -> "^";
            case 1 -> "/";
            case 2 -> ">";
            case 3 -> "\\";
            case 4 -> "v";
            case 5 -> "/";
            case 6 -> "<";
            default -> "\\";
        };
    }

    private static String time(double seconds) {
        int total = (int) Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }
}
