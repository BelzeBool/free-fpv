package com.belzebool.freefpv.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything tunable, stored as {@code config/freefpv.json}. Re-read every time a drone is launched,
 * so edits apply without restarting the game.
 */
public final class DroneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public General general = new General();
    public Camera camera = new Camera();
    public Fpv fpv = new Fpv();
    public CameraDrone cameraDrone = new CameraDrone();
    public Controls controls = new Controls();
    public Gamepad gamepad = new Gamepad();
    public Compat compat = new Compat();
    public Effects effects = new Effects();
    public Tools tools = new Tools();

    public static final class General {
        /** Mode used the first time a drone is launched: CINE, NORMAL, SPORT, ANGLE or ACRO. */
        public String startMode = "NORMAL";
        /** Maximum distance from the pilot in blocks. Also capped by render distance. */
        public double maxRange = 256;
        /** Battery life at hover in minutes; 0 disables the battery. */
        public double batteryMinutes = 20;
        /** Land the pilot back in their body when they take damage. */
        public boolean exitOnDamage = true;
        /** Hide hotbar, health and effects while flying (chat stays). */
        public boolean hideVanillaHud = true;
        /** Seconds the crash view is shown before the camera returns to the pilot. */
        public double crashScreenSeconds = 2.5;
        /** Propeller sound volume, 0..1. */
        public double soundVolume = 0.8;
        /** Beeps for low battery and weak signal, and a click on mode changes. */
        public boolean beeps = true;
    }

    public static final class Camera {
        /** Horizontal field of view in degrees, like a real FPV camera lens. */
        public double fpvFov = 118;
        /** FPV camera uptilt in degrees. Freestyle pilots use 20-35. */
        public double fpvUptilt = 25;
        /** Horizontal field of view of the gimbal camera at 1x zoom. */
        public double cameraDroneFov = 84;
        public double maxZoom = 4;
        /** Analog-video look for FPV: scanlines, vignette, breakup near the range limit. */
        public boolean analogEffects = true;
        public boolean showOsd = true;
        /** Camera shake from motors, wind and impacts, 0 = off, 1 = default. Gimbal cameras shake much less. */
        public double shake = 1.0;
        /** Camera drone field of view widens slightly with speed in Normal and Sport, 0 = off. */
        public double speedFov = 1.0;
        /** Short "connecting" transition when the video feed starts and ends. */
        public boolean transitions = true;
    }

    public static final class Fpv {
        /** Thrust to weight ratio. 4-6 feels like a 5" freestyle quad. */
        public double thrustToWeight = 5.0;
        public double maxAngle = 55;
        /** Betaflight "Actual" rates, degrees per second. */
        public double centerRate = 200;
        public double maxRate = 670;
        public double expo = 0.54;
        public double yawCenterRate = 180;
        public double yawMaxRate = 480;
        public double linearDrag = 0.12;
        public double quadraticDrag = 0.028;
        /** Impact speed in blocks/s above which the quad breaks. */
        public double crashSpeed = 7.0;
    }

    public static final class CameraDrone {
        public double cineSpeed = 3, normalSpeed = 8, sportSpeed = 16;
        public double cineClimb = 1.5, normalClimb = 4, sportClimb = 6;
        public double cineYawRate = 35, normalYawRate = 90, sportYawRate = 150;
        public double cineAccel = 2.5, normalAccel = 7, sportAccel = 14;
        /** Gimbal tilt limits in degrees, negative looks down. */
        public double gimbalMin = -90, gimbalMax = 20;
        public double gimbalSpeed = 60;
        /** Crashing into walls is impossible for camera drones; they stop like DJI obstacle sensing. */
        public boolean obstacleAvoidance = true;
        /** Return to home climbs to at least this many blocks above the pilot before flying back. */
        public double rthHeight = 12;
        /** Start return to home by itself when the battery runs low, like DJI. */
        public boolean autoRthOnLowBattery = true;
    }

    public static final class Controls {
        /** Multiplier on top of the vanilla mouse sensitivity while flying. */
        public double mouseSensitivity = 1.0;
        public boolean invertMouseY = false;
        /** FPV keyboard throttle speed in Acro, full range per second. */
        public double keyboardThrottleSpeed = 0.9;
        /**
         * Angle mode on keyboard: Space/Shift ask for climb or descent and the quad holds its height when released.
         * Tap for a gentle climb, hold to punch out. Off = raw throttle like Acro.
         */
        public boolean keyboardAltitudeHold = true;
        /** Mouse smoothing for the camera drone (Cine is smoothest). 0 = raw mouse, 1 = default, 2 = extra smooth. */
        public double mouseSmoothing = 1.0;
        /** Show the controls panel for a few seconds after launch. The Controls help key toggles it any time. */
        public boolean showHelpOnLaunch = true;
    }

    public static final class Gamepad {
        public boolean enabled = true;
        /** 1 = throttle+roll right stick (Mode 1), 2 = throttle+yaw left stick (Mode 2, default). */
        public int stickMode = 2;
        public double deadzone = 0.08;
        public double expo = 0.25;
        /**
         * Gamepad sticks spring back to centre, so by default centre = hover in FPV modes.
         * Turn off for RC transmitters in joystick mode with a non-centering throttle.
         */
        public boolean centeredThrottle = true;
        /**
         * Joysticks without a gamepad mapping (RC transmitters, flight sticks) use these raw axis indices.
         * Defaults match EdgeTX/OpenTX USB joystick (AETR).
         */
        public int rawRollAxis = 0, rawPitchAxis = 1, rawThrottleAxis = 2, rawYawAxis = 3;
        public boolean rawInvertPitch = true, rawInvertThrottle = false;
        /** Hold this long (seconds) on Back/Share/Create to launch the drone from the controller. */
        public double launchHoldSeconds = 0.6;
        /** Rumble on impacts and a light motor buzz in FPV (DualSense, DualShock, Xbox). */
        public boolean rumble = true;
        /** Colour the DualSense / DualShock light bar by flight mode and battery. */
        public boolean lightBar = true;
    }

    public static final class Compat {
        /** Emotecraft emote played while piloting, matched by name or UUID. Empty disables it. */
        public String emote = "FPV Pilot";
        public boolean useEmotecraft = true;
        public boolean useFigura = true;
    }

    public static final class Effects {
        /** Dust kicked up by prop wash near the ground, water spray, crash debris and smoke. */
        public boolean particles = true;
        /** Wind rush that grows with FPV speed. */
        public boolean windSound = true;
        /** Hands on the remote (and goggles for FPV) on the pilot's body, seen by everyone with the mod. */
        public boolean pilotPose = true;
    }

    public static final class Tools {
        /** Extra 10th slot next to the hotbar that holds the drone remotes and other multitools. */
        public boolean enabled = true;
        /** Scrolling past slot 9 (or before slot 1) lands on the tool slot. */
        public boolean scrollIntoToolSlot = true;
        /** Hold the Tools wheel key to pick a tool; releasing selects the hovered one. */
        public boolean releaseToSelect = true;
        /** Last selected tool, restored on start. */
        public String selected = "freefpv:camera_remote";
    }

    public double speedFor(FlightMode mode) {
        return switch (mode) {
            case CINE -> cameraDrone.cineSpeed;
            case SPORT -> cameraDrone.sportSpeed;
            default -> cameraDrone.normalSpeed;
        };
    }

    public double climbFor(FlightMode mode) {
        return switch (mode) {
            case CINE -> cameraDrone.cineClimb;
            case SPORT -> cameraDrone.sportClimb;
            default -> cameraDrone.normalClimb;
        };
    }

    public double yawRateFor(FlightMode mode) {
        return switch (mode) {
            case CINE -> cameraDrone.cineYawRate;
            case SPORT -> cameraDrone.sportYawRate;
            default -> cameraDrone.normalYawRate;
        };
    }

    public double accelFor(FlightMode mode) {
        return switch (mode) {
            case CINE -> cameraDrone.cineAccel;
            case SPORT -> cameraDrone.sportAccel;
            default -> cameraDrone.normalAccel;
        };
    }

    public static DroneConfig load(Path file) {
        DroneConfig config = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, DroneConfig.class);
            } catch (IOException | JsonParseException e) {
                com.belzebool.freefpv.FreeFpv.LOGGER.warn("Could not read {}, using defaults: {}", file, e.getMessage());
            }
        }
        if (config == null) config = new DroneConfig();
        config.fillMissing();
        config.save(file);
        return config;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            com.belzebool.freefpv.FreeFpv.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
        }
    }

    /** Sections missing from an older file come back as null from Gson. */
    private void fillMissing() {
        if (general == null) general = new General();
        if (camera == null) camera = new Camera();
        if (fpv == null) fpv = new Fpv();
        if (cameraDrone == null) cameraDrone = new CameraDrone();
        if (controls == null) controls = new Controls();
        if (gamepad == null) gamepad = new Gamepad();
        if (compat == null) compat = new Compat();
        if (effects == null) effects = new Effects();
        if (tools == null) tools = new Tools();
    }
}
