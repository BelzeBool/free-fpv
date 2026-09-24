package com.belzebool.freefpv.core;

/**
 * Pilot input for one physics frame, already merged from keyboard, mouse and controller.
 * Sticks are in [-1, 1]; positive pitch = stick forward (nose down), positive roll/yaw = right.
 */
public final class DroneInput {
    /**
     * Camera drones: vertical speed request, centre = hold altitude.
     * FPV: throttle position, see {@link #throttleAbsolute}.
     */
    public double throttle;
    public double yaw;
    public double pitch;
    public double roll;
    /** Gimbal tilt speed request, positive tilts the camera up. */
    public double gimbal;

    /** FPV only: when true {@link #throttle} is 0..1 motor output, otherwise it is a centred stick. */
    public boolean throttleAbsolute;

    /** Direct rotations from the mouse this frame, in degrees. Applied without rate limiting. */
    public double mouseYawDeg;
    public double mousePitchDeg;
    public double mouseRollDeg;

    public void clear() {
        throttle = yaw = pitch = roll = gimbal = 0;
        mouseYawDeg = mousePitchDeg = mouseRollDeg = 0;
        throttleAbsolute = false;
    }
}
