package com.belzebool.freefpv.core.race;

import java.util.Locale;

public final class RaceTime {
    private RaceTime() {
    }

    /** 1:23.456 or 23.456 */
    public static String format(long ms) {
        if (ms < 0) ms = 0;
        long minutes = ms / 60000, seconds = ms / 1000 % 60, millis = ms % 1000;
        return minutes > 0 ? String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis)
            : String.format(Locale.ROOT, "%d.%03d", seconds, millis);
    }

    /** +0.52 / -1.30 */
    public static String delta(long ms) {
        return String.format(Locale.ROOT, "%s%.2f", ms < 0 ? "-" : "+", Math.abs(ms) / 1000.0);
    }
}
