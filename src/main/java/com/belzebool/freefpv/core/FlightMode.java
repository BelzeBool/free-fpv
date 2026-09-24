package com.belzebool.freefpv.core;

/**
 * Flight modes. The first three emulate a GPS-stabilised camera drone (DJI Mini/Air/Mavic),
 * the last two a freestyle FPV quad (Betaflight Angle / Acro).
 */
public enum FlightMode {
    CINE(Family.CAMERA, "C", "Cine"),
    NORMAL(Family.CAMERA, "N", "Normal"),
    SPORT(Family.CAMERA, "S", "Sport"),
    ANGLE(Family.FPV, "ANGLE", "Angle"),
    ACRO(Family.FPV, "ACRO", "Acro");

    public enum Family { CAMERA, FPV }

    public final Family family;
    public final String osdLabel;
    public final String displayName;

    FlightMode(Family family, String osdLabel, String displayName) {
        this.family = family;
        this.osdLabel = osdLabel;
        this.displayName = displayName;
    }

    public boolean isFpv() {
        return family == Family.FPV;
    }

    /** Next mode inside the same family: camera drones cycle C/N/S, FPV quads cycle Angle/Acro. */
    public FlightMode nextInFamily() {
        FlightMode[] all = values();
        for (int i = 1; i <= all.length; i++) {
            FlightMode candidate = all[(ordinal() + i) % all.length];
            if (candidate.family == family) return candidate;
        }
        return this;
    }

    public static FlightMode parse(String name, FlightMode fallback) {
        if (name == null) return fallback;
        for (FlightMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name.trim())) return mode;
        }
        return fallback;
    }
}
