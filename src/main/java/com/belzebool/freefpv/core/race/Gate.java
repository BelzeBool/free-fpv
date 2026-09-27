package com.belzebool.freefpv.core.race;

import java.util.UUID;

/**
 * One marker of a track. {@code x, y, z} is the base (where it stands on the ground; for a dive gate the centre of the
 * flat opening), {@code yaw} the direction the pilot must fly through it, in Minecraft degrees (0 = south, 90 = west).
 */
public final class Gate {
    public String id = UUID.randomUUID().toString();
    public GateType type = GateType.STANDARD;
    public double x, y, z;
    public float yaw;
    public float scale = 1;

    public Gate() {
    }

    public Gate(GateType type, double x, double y, double z, float yaw, float scale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.scale = scale;
    }

    public double width() {
        return type.width * scale;
    }

    public double height() {
        return type.height * scale;
    }

    /** Centre of the opening. */
    public double cx() {
        return x;
    }

    public double cy() {
        return type.horizontal() ? y : y + (type.lift + type.height / 2) * scale;
    }

    public double cz() {
        return z;
    }

    /** Travel direction, unit vector. */
    public double nx() {
        return -Math.sin(Math.toRadians(yaw));
    }

    public double nz() {
        return Math.cos(Math.toRadians(yaw));
    }

    /**
     * If the straight path {@code a -> b} passes through the opening in the right direction, returns the fraction
     * along the path where it does (0..1), otherwise -1. {@code margin} widens the opening (the drone has a size).
     */
    public double crossing(double ax, double ay, double az, double bx, double by, double bz, double margin) {
        double cx = cx(), cy = cy(), cz = cz();
        if (type.horizontal()) {
            // Dive gate: plane y = cy, crossed going down.
            double da = ay - cy, db = by - cy;
            if (!(da > 0 && db <= 0)) return -1;
            double t = da / (da - db);
            double px = ax + (bx - ax) * t - cx, pz = az + (bz - az) * t - cz;
            // Local axes: along the yaw direction and across it.
            double along = px * nx() + pz * nz();
            double across = px * nz() - pz * nx();
            return Math.abs(across) <= width() / 2 + margin && Math.abs(along) <= height() / 2 + margin ? t : -1;
        }
        double nx = nx(), nz = nz();
        double da = (ax - cx) * nx + (az - cz) * nz;
        double db = (bx - cx) * nx + (bz - cz) * nz;
        if (!(da < 0 && db >= 0)) return -1;
        double t = da / (da - db);
        double px = ax + (bx - ax) * t - cx, py = ay + (by - ay) * t - cy, pz = az + (bz - az) * t - cz;
        double across = px * nz - pz * nx;
        if (type == GateType.FLAG) {
            // Flags: pass anywhere near the pole, low enough to count.
            double height = py + height() / 2;
            return Math.abs(across) <= width() / 2 + margin && height >= -margin && height <= height() + margin ? t : -1;
        }
        return Math.abs(across) <= width() / 2 + margin && Math.abs(py) <= height() / 2 + margin ? t : -1;
    }

    public Gate copy() {
        Gate g = new Gate(type, x, y, z, yaw, scale);
        g.id = id;
        return g;
    }
}
