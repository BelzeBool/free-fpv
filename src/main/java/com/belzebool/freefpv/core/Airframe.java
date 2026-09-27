package com.belzebool.freefpv.core;

/**
 * Drone builds. Each one has its own model and changes the FPV flight model: a cinewhoop is slow but shrugs off
 * bumps thanks to its ducts, a 7 inch long-range quad glides far on a big battery.
 */
public enum Airframe {
    MINI("mini", false, "drone_camera", 0.55f, 1, 1, 1, 0.3, 1, 1),
    FREESTYLE("freestyle", true, "drone_fpv", 0.5f, 1, 1, 1, 0.3, 1, 1),
    CINEWHOOP("cinewhoop", true, "drone_cinewhoop", 0.42f, 0.72, 1.7, 1.8, 0.55, 0.8, 0.85),
    LONG_RANGE("long_range", true, "drone_longrange", 0.62f, 0.85, 0.7, 1.0, 0.25, 2.6, 0.8),
    AVATA("avata", true, "drone_avata", 0.5f, 0.78, 1.45, 1.7, 0.5, 1.3, 0.9);

    public final String id;
    public final boolean fpv;
    public final String model;
    /** Size of the display model in the world. */
    public final float scale;
    public final double thrust, drag, toughness, bounce, battery, rates;

    Airframe(String id, boolean fpv, String model, float scale, double thrust, double drag, double toughness,
             double bounce, double battery, double rates) {
        this.id = id;
        this.fpv = fpv;
        this.model = model;
        this.scale = scale;
        this.thrust = thrust;
        this.drag = drag;
        this.toughness = toughness;
        this.bounce = bounce;
        this.battery = battery;
        this.rates = rates;
    }

    public static Airframe parse(String id, Airframe fallback) {
        for (Airframe a : values()) if (a.id.equals(id) || a.name().equalsIgnoreCase(id)) return a;
        return fallback;
    }

    public static Airframe byOrdinal(int ordinal) {
        Airframe[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FREESTYLE;
    }

    /** The user's settings with this build's flight characteristics applied. */
    public DroneConfig apply(DroneConfig base) {
        DroneConfig c = base.copy();
        c.fpv.thrustToWeight *= thrust;
        c.fpv.linearDrag *= drag;
        c.fpv.quadraticDrag *= drag;
        c.fpv.crashSpeed *= toughness;
        c.fpv.centerRate *= rates;
        c.fpv.maxRate *= rates;
        c.fpv.yawCenterRate *= rates;
        c.fpv.yawMaxRate *= rates;
        c.general.batteryMinutes *= battery;
        return c;
    }
}
