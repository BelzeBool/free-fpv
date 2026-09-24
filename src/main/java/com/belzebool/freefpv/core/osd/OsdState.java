package com.belzebool.freefpv.core.osd;

import com.belzebool.freefpv.core.DronePhysics;
import com.belzebool.freefpv.core.FlightMode;

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
}
