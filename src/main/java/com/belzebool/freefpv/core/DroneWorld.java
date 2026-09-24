package com.belzebool.freefpv.core;

import org.joml.Vector3d;

/** What the physics needs from the game world. Implemented per Minecraft version. */
public interface DroneWorld {
    /**
     * Moves an axis-aligned box centred at {@code center} with half extents {@code half} by {@code motion},
     * clipping against blocks. Writes the allowed motion into {@code result}.
     */
    void collide(Vector3d center, Vector3d half, Vector3d motion, Vector3d result);

    /** True when the point is inside water or lava. */
    boolean isInLiquid(double x, double y, double z);

    /** True when terrain around the point is loaded on the client. Unloaded space is treated as a wall. */
    boolean isLoaded(double x, double y, double z);
}
