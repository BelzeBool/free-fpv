package com.belzebool.freefpv.core.race;

/**
 * Kinds of track markers. Sizes are the opening at scale 1, in blocks; the model is scaled with the gate.
 * {@link #START} is always the first gate of a track and doubles as the finish line.
 */
public enum GateType {
    START("start", 3.0, 2.2, 0.35),
    STANDARD("standard", 2.0, 2.0, 0.35),
    ARCH("arch", 2.6, 3.4, 0.2),
    FLAG("flag", 8.0, 12.0, 0.0),
    DIVE("dive", 2.0, 2.0, 0.0);

    public final String id;
    /** Opening width and height at scale 1. */
    public final double width;
    public final double height;
    /** Gap between the base and the bottom of the opening (legs of the gate). */
    public final double lift;

    GateType(String id, double width, double height, double lift) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.lift = lift;
    }

    /** Dive gates lie flat: the pilot flies down through them. Flags count when the drone passes near the pole. */
    public boolean horizontal() {
        return this == DIVE;
    }

    public String model() {
        return "freefpv:gate_" + id;
    }

    public GateType nextPlaceable() {
        return switch (this) {
            case STANDARD -> ARCH;
            case ARCH -> DIVE;
            case DIVE -> FLAG;
            default -> STANDARD;
        };
    }

    public static GateType parse(String id, GateType fallback) {
        for (GateType t : values()) if (t.id.equals(id) || t.name().equalsIgnoreCase(id)) return t;
        return fallback;
    }
}
