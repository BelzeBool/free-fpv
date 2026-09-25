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
    /**
     * FPV Angle with a centred stick: the quad holds its height while the stick is centred and idles on the ground,
     * so keyboard and gamepad pilots don't have to manage hover throttle.
     */
    public boolean altitudeHold;

    /** Direct rotations from the mouse this frame, in degrees. Applied without rate limiting. */
    public double mouseYawDeg;
    public double mousePitchDeg;
    public double mouseRollDeg;

    public void clear() {
        throttle = yaw = pitch = roll = gimbal = 0;
        mouseYawDeg = mousePitchDeg = mouseRollDeg = 0;
        throttleAbsolute = false;
        altitudeHold = false;
    }

    /** True when any stick is away from centre, used to cancel autopilots like return to home. */
    public boolean sticksActive(double threshold) {
        return Math.abs(pitch) > threshold || Math.abs(roll) > threshold || Math.abs(yaw) > threshold
            || Math.abs(throttle) > threshold;
    }
}
