package com.belzebool.freefpv.core.osd;

import com.belzebool.freefpv.core.DronePhysics;
import com.belzebool.freefpv.core.FlightMode;

import java.util.List;

/** Snapshot of everything the on-screen display shows. Filled by the drone controller every frame. */
public final class OsdState {
    public FlightMode mode = FlightMode.NORMAL;
    public double altitude;
    public double distanceHome;
    public double distancePilot;
    public double horizontalSpeed;
    public double verticalSpeed;
    public double battery = 1;
    public boolean batteryEnabled = true;
    public double batteryMinutes;
    public double voltage;
    public double flightTime;
    public double signal = 1;
    public double throttle;
    public double gimbal;
    public double compass;
    public double pitch;
    public double roll;
    public double zoom = 1;
    public double verticalFov = 70;
    /** Bearing from the drone to the pilot relative to the nose, degrees, positive = right. */
    public double homeBearing;
    public boolean recording;
    public boolean crashed;
    public DronePhysics.CrashReason crashReason = DronePhysics.CrashReason.NONE;
    public double crashTimer;
    public double maxRange = 256;
    public String controller = "";
    public String hint = "";
    public double hintTimer;
    /** Seconds since the OSD appeared, drives blinking and noise. */
    public double time;
    public boolean analogEffects = true;
    public boolean showOsd = true;

    /** FPV: motor output that holds a hover, for the mark on the throttle bar. */
    public double hoverThrottle = 0.2;
    /** FPV Angle on keyboard/gamepad: the quad holds its height, shown as "ALT HOLD". */
    public boolean altitudeHold;
    public DronePhysics.Autopilot autopilot = DronePhysics.Autopilot.NONE;
    /** Seconds since the video feed started; the first moments show a connecting transition. */
    public double feedAge = 10;
    public boolean transitions = true;
    public String connectingText = "Connecting...";
    /** Controls panel: pairs of {key, action}, faded by {@link #helpAlpha}. */
    public List<String[]> help = List.of();
    public String helpTitle = "";
    public double helpAlpha;
}
