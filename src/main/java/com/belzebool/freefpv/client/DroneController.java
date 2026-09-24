package com.belzebool.freefpv.client;

import com.belzebool.freefpv.compat.EmotecraftCompat;
import com.belzebool.freefpv.core.DroneConfig;
import com.belzebool.freefpv.core.DroneInput;
import com.belzebool.freefpv.core.DronePhysics;
import com.belzebool.freefpv.core.FlightMode;
import com.belzebool.freefpv.core.input.Gamepad;
import com.belzebool.freefpv.core.osd.OsdCanvas;
import com.belzebool.freefpv.core.osd.OsdPainter;
import com.belzebool.freefpv.core.osd.OsdState;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.platform.Platform;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.Locale;

//? if >=1.21.2 {
import net.minecraft.client.player.ClientInput;
//?} else
//import net.minecraft.client.player.Input;

/**
 * Client side of a flight: owns the physics, turns keyboard/mouse/controller into stick input, drives the camera,
 * OSD, sound and network sync. Everything version-specific lives in the mixins and {@link GuiCanvas}.
 */
public final class DroneController {
    public static final DroneController INSTANCE = new DroneController();

    private final DronePhysics physics = new DronePhysics();
    private final DroneInput input = new DroneInput();
    private final Gamepad gamepad = new Gamepad();
    private final OsdState osd = new OsdState();
    private final OsdPainter painter = new OsdPainter();
    private final McDroneWorld world = new McDroneWorld();
    private DroneConfig config = new DroneConfig();

    private boolean flying;
    private FlightMode mode = FlightMode.NORMAL;
    private FlightMode lastCameraMode = FlightMode.NORMAL;
    private FlightMode lastFpvMode = FlightMode.ACRO;
    private boolean modeFromConfig = true;
    private LocalPlayer pilot;
    private CameraType savedCameraType;
    private Object savedInput;
    private float lastHealth;

    private long lastFrameNanos;
    private double mouseX, mouseY;
    private double zoom = 1, zoomTarget = 1;
    private double keyboardThrottle;
    private double lastThrottle;
    private boolean lastThrottleAbsolute;
    private boolean recording;
    private double crashTimer;
    private double launchHold;
    private boolean launchLatch;
    private int ownDroneId = -1;
    private double maxRange = 256;
    private double verticalFov = 70;
    private String hint = "";
    private double hintTimer;
    private double osdTime;

    private final Vector3d cameraPos = new Vector3d();
    private final Quaterniond cameraRot = new Quaterniond();
    private final Vector3d pilotEye = new Vector3d();

    private DroneController() {
    }

    // ------------------------------------------------------------------ lifecycle

    public void init() {
        config = DroneConfig.load(configFile());
        EmotecraftCompat.installBundledEmote();
    }

    private static Path configFile() {
        return Platform.INSTANCE.configDir().resolve("freefpv.json");
    }

    public boolean isFlying() {
        return flying;
    }

    public void start(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || flying) return;
        config = DroneConfig.load(configFile());
        gamepad.loadMappings(Platform.INSTANCE.configDir().resolve("freefpv").resolve("gamecontrollerdb.txt"));
        if (modeFromConfig) {
            mode = FlightMode.parse(config.general.startMode, FlightMode.NORMAL);
            if (mode.isFpv()) lastFpvMode = mode;
            else lastCameraMode = mode;
            modeFromConfig = false;
        }

        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double heading = Math.atan2(-fx, -fz);
        Vec3 spawn = findLaunchSpot(mc, player, fx, fz);
        physics.reset(spawn.x, spawn.y, spawn.z, heading, mode);

        savedCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        //? if >=1.21.2 {
        savedInput = player.input;
        player.input = new ClientInput();
        //?} else {
        /*savedInput = player.input;
        player.input = new Input();
        *///?}

        pilot = player;
        flying = true;
        lastHealth = player.getHealth();
        lastFrameNanos = 0;
        mouseX = mouseY = 0;
        zoom = zoomTarget = 1;
        keyboardThrottle = 0;
        lastThrottle = 0;
        crashTimer = 0;
        osdTime = 0;
        ownDroneId = -1;

        if (config.compat.useEmotecraft) EmotecraftCompat.start(config.compat.emote);
        mc.getSoundManager().play(new DroneSound(this::ownSoundSample));
        showHint(mode.displayName + "  -  " + keyName(Keys.LAUNCH) + ": land  " + keyName(Keys.MODE) + ": mode  " + keyName(Keys.DRONE_TYPE) + ": FPV/camera");
    }

    /** Puts the drone on the ground a step in front of the pilot, or at eye level if that spot is blocked. */
    private Vec3 findLaunchSpot(Minecraft mc, LocalPlayer player, double fx, double fz) {
        double hx = physics.half.x, hy = physics.half.y;
        Vec3[] candidates = {
            new Vec3(player.getX() + fx * 1.4, player.getY() + hy + 0.02, player.getZ() + fz * 1.4),
            new Vec3(player.getX() + fx * 0.9, player.getEyeY() - 0.3, player.getZ() + fz * 0.9),
            player.getEyePosition()
        };
        for (Vec3 c : candidates) {
            AABB box = new AABB(c.x - hx, c.y - hy, c.z - hx, c.x + hx, c.y + hy, c.z + hx);
            if (mc.level.noCollision(box)) return c;
        }
        return player.getEyePosition();
    }

    public void stop(Minecraft mc) {
        if (!flying) return;
        flying = false;
        LocalPlayer player = mc.player;
        if (player != null && player == pilot) {
            if (savedInput != null) {
                //? if >=1.21.2 {
                player.input = (ClientInput) savedInput;
                //?} else
                //player.input = (Input) savedInput;
            }
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && savedCameraType != null) {
                mc.options.setCameraType(savedCameraType);
            }
        }
        savedInput = null;
        pilot = null;
        EmotecraftCompat.stop();
        if (mc.getConnection() != null && Platform.INSTANCE.canSendToServer(DroneStatePayload.TYPE)) {
            Platform.INSTANCE.sendToServer(DroneStatePayload.inactive());
        }
    }

    /** Called when the world or player object went away underneath us (disconnect, dimension change). */
    private void abandon(Minecraft mc) {
        flying = false;
        pilot = null;
        savedInput = null;
        EmotecraftCompat.stop();
        if (savedCameraType != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(savedCameraType);
        }
    }

    // ------------------------------------------------------------------ ticks

    public void clientTick(Minecraft mc) {
        RemoteDroneSounds.tick(mc, ownDroneId, config.general.soundVolume);
        if (!flying) {
            idleTick(mc);
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || player != pilot || mc.level == null) {
            abandon(mc);
            return;
        }

        while (Keys.LAUNCH.consumeClick()) {
            stop(mc);
            return;
        }
        while (Keys.MODE.consumeClick()) cycleMode();
        while (Keys.DRONE_TYPE.consumeClick()) switchDroneType();
        while (Keys.RECORD.consumeClick()) toggleRecording();
        while (Keys.OSD.consumeClick()) toggleOsd();
        while (Keys.GIMBAL_RESET.consumeClick()) resetGimbal();

        if (!player.isAlive()) {
            stop(mc);
            return;
        }
        float health = player.getHealth();
        if (config.general.exitOnDamage && health < lastHealth - 0.01f) {
            stop(mc);
            showHint("Pilot took damage");
            return;
        }
        lastHealth = health;

        if (physics.crashed && crashTimer > config.general.crashScreenSeconds) {
            stop(mc);
            return;
        }

        if (Platform.INSTANCE.canSendToServer(DroneStatePayload.TYPE)) {
            Platform.INSTANCE.sendToServer(new DroneStatePayload(true, mode.isFpv(),
                physics.pos.x, physics.pos.y, physics.pos.z,
                (float) physics.att.x, (float) physics.att.y, (float) physics.att.z, (float) physics.att.w,
                (float) physics.motor));
        }
    }

    private void idleTick(Minecraft mc) {
        while (Keys.MODE.consumeClick()) {
        }
        while (Keys.DRONE_TYPE.consumeClick()) {
        }
        while (Keys.RECORD.consumeClick()) {
        }
        while (Keys.OSD.consumeClick()) {
        }
        while (Keys.GIMBAL_RESET.consumeClick()) {
        }
        boolean launch = false;
        while (Keys.LAUNCH.consumeClick()) launch = true;
        if (mc.player == null || mc.level == null) return;

        if (config.gamepad.enabled && screen(mc) == null && gamepad.poll() && gamepad.kind() == Gamepad.Kind.GAMEPAD) {
            if (gamepad.down(Gamepad.BACK)) {
                launchHold += 0.05;
                if (launchHold >= config.gamepad.launchHoldSeconds && !launchLatch) {
                    launchLatch = true;
                    launch = true;
                }
            } else {
                launchHold = 0;
                launchLatch = false;
            }
        }
        if (launch && screen(mc) == null) start(mc);
    }

    /** Runs once per rendered frame, from the camera update, before the camera pose is used. */
    public void onFrame(Minecraft mc) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1e9;
        lastFrameNanos = now;
        if (!flying || pilot == null) return;
        if (mc.isPaused()) dt = 0;
        dt = Math.min(dt, 0.1);

        buildInput(mc, dt);

        pilotEye.set(pilot.getX(), pilot.getEyeY(), pilot.getZ());
        maxRange = Math.min(config.general.maxRange, Math.max(32, (mc.options.getEffectiveRenderDistance() - 1) * 16));
        double alpha = physics.advance(dt, input, mode, config, world, pilotEye, maxRange);
        physics.cameraPose(alpha, mode, config, cameraPos, cameraRot);

        zoom += (zoomTarget - zoom) * (1 - Math.exp(-dt * 8));
        double aspect = Math.max(0.1, (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()));
        double horizontal = Math.toRadians(mode.isFpv() ? config.camera.fpvFov : config.camera.cameraDroneFov);
        double vertical = 2 * Math.atan(Math.tan(horizontal / 2) / aspect);
        if (!mode.isFpv()) vertical = 2 * Math.atan(Math.tan(vertical / 2) / zoom);
        verticalFov = Math.max(10, Math.min(170, Math.toDegrees(vertical)));

        if (physics.crashed) crashTimer += dt;
        hintTimer = Math.max(0, hintTimer - dt);
        osdTime += dt;
        updateOsd();
    }

    // ------------------------------------------------------------------ input

    public void onMouseTurn(double dx, double dy) {
        mouseX += dx;
        mouseY += dy;
    }

    public void onScroll(double amount) {
        if (mode.isFpv()) return;
        zoomTarget = DronePhysics.clamp(zoomTarget * Math.pow(1.15, amount), 1, Math.max(1, config.camera.maxZoom));
    }

    /** Swallows gameplay keys so clicks and hotbar keys don't act on the pilot's body. */
    public void drainGameplayKeys(Options o) {
        while (o.keyTogglePerspective.consumeClick()) {
        }
        while (o.keyAttack.consumeClick()) {
        }
        while (o.keyUse.consumeClick()) {
        }
        while (o.keyPickItem.consumeClick()) {
        }
        while (o.keyDrop.consumeClick()) {
        }
        while (o.keySwapOffhand.consumeClick()) {
        }
        for (KeyMapping slot : o.keyHotbarSlots) {
            while (slot.consumeClick()) {
            }
        }
        o.keyAttack.setDown(false);
        o.keyUse.setDown(false);
    }

    private void buildInput(Minecraft mc, double dt) {
        input.clear();
        double mdx = mouseX, mdy = mouseY;
        mouseX = mouseY = 0;

        if (screen(mc) != null) {
            // Chat or inventory open: hands off the sticks but keep the throttle where it was.
            input.throttle = mode.isFpv() ? lastThrottle : 0;
            input.throttleAbsolute = lastThrottleAbsolute;
            return;
        }
        boolean pad = config.gamepad.enabled && gamepad.poll() && gamepad.kind() != Gamepad.Kind.NONE;

        Options o = mc.options;
        double forward = axis(o.keyUp, o.keyDown);
        double strafe = axis(o.keyRight, o.keyLeft);
        double vertical = axis(o.keyJump, o.keyShift);
        double sens = 0.15 * config.controls.mouseSensitivity;
        boolean padLook = pad && gamepad.recentlyUsed(0.5);
        double mouseYaw = padLook ? 0 : mdx * sens;
        double mousePitch = padLook ? 0 : -mdy * sens * (config.controls.invertMouseY ? -1 : 1);

        if (!mode.isFpv()) {
            input.pitch = forward;
            input.roll = strafe;
            input.throttle = vertical;
            input.mouseYawDeg = mouseYaw;
            input.mousePitchDeg = mousePitch;
        } else {
            double speed = config.controls.keyboardThrottleSpeed;
            if (vertical > 0) keyboardThrottle += speed * dt;
            else if (vertical < 0) keyboardThrottle -= speed * dt;
            else if (mode == FlightMode.ANGLE && !physics.onGround) {
                keyboardThrottle += (hoverThrottle() - keyboardThrottle) * (1 - Math.exp(-dt * 1.5));
            }
            keyboardThrottle = DronePhysics.clamp(keyboardThrottle, 0, 1);
            input.throttle = keyboardThrottle;
            input.throttleAbsolute = true;
            input.pitch = forward;
            if (mode == FlightMode.ANGLE) {
                input.roll = strafe;
                input.mouseYawDeg = mouseYaw;
            } else {
                input.yaw = strafe;
                input.mousePitchDeg = mousePitch;
                input.mouseRollDeg = mouseYaw;
            }
        }

        if (pad) applyGamepad(mc, dt);
        lastThrottle = input.throttle;
        lastThrottleAbsolute = input.throttleAbsolute;
    }

    private void applyGamepad(Minecraft mc, double dt) {
        DroneConfig.Gamepad g = config.gamepad;
        double yaw, throttle, roll, pitch;
        boolean rc = gamepad.kind() == Gamepad.Kind.JOYSTICK;
        if (rc) {
            roll = Gamepad.shape(gamepad.axis(g.rawRollAxis), 0.02, 0);
            pitch = Gamepad.shape(gamepad.axis(g.rawPitchAxis) * (g.rawInvertPitch ? -1 : 1), 0.02, 0);
            yaw = Gamepad.shape(gamepad.axis(g.rawYawAxis), 0.02, 0);
            throttle = gamepad.axis(g.rawThrottleAxis) * (g.rawInvertThrottle ? -1 : 1);
        } else {
            double lx = gamepad.axis(Gamepad.LEFT_X), ly = -gamepad.axis(Gamepad.LEFT_Y);
            double rx = gamepad.axis(Gamepad.RIGHT_X), ry = -gamepad.axis(Gamepad.RIGHT_Y);
            yaw = Gamepad.shape(lx, g.deadzone, g.expo);
            roll = Gamepad.shape(rx, g.deadzone, g.expo);
            if (g.stickMode == 1) {
                pitch = Gamepad.shape(ly, g.deadzone, g.expo);
                throttle = Gamepad.shape(ry, g.deadzone, 0);
            } else {
                throttle = Gamepad.shape(ly, g.deadzone, 0);
                pitch = Gamepad.shape(ry, g.deadzone, g.expo);
            }
        }

        boolean sticks = Math.abs(yaw) + Math.abs(roll) + Math.abs(pitch) + Math.abs(throttle) > 0;
        if (sticks || rc) {
            input.yaw = merge(input.yaw, yaw);
            input.roll = merge(input.roll, roll);
            input.pitch = merge(input.pitch, pitch);
        }

        if (!mode.isFpv()) {
            input.throttle = merge(input.throttle, throttle);
            if (!rc) {
                double lt = (gamepad.axis(Gamepad.LEFT_TRIGGER) + 1) / 2, rt = (gamepad.axis(Gamepad.RIGHT_TRIGGER) + 1) / 2;
                input.gimbal = merge(input.gimbal, rt - lt);
                if (gamepad.down(Gamepad.RB)) zoomTarget = Math.min(config.camera.maxZoom, zoomTarget * Math.exp(dt * 1.2));
                if (gamepad.down(Gamepad.LB)) zoomTarget = Math.max(1, zoomTarget / Math.exp(dt * 1.2));
            }
        } else if (rc || !g.centeredThrottle) {
            input.throttle = (throttle + 1) / 2;
            input.throttleAbsolute = true;
            keyboardThrottle = input.throttle;
        } else if (gamepad.recentlyUsed(1.5)) {
            input.throttle = throttle;
            input.throttleAbsolute = false;
            keyboardThrottle = physics.motor;
        }

        if (rc) return;
        if (gamepad.pressed(Gamepad.START)) stop(mc);
        if (gamepad.pressed(Gamepad.Y)) cycleMode();
        if (gamepad.pressed(Gamepad.BACK)) switchDroneType();
        if (gamepad.pressed(Gamepad.A)) toggleRecording();
        if (gamepad.pressed(Gamepad.X)) toggleOsd();
        if (gamepad.pressed(Gamepad.B)) resetGimbal();
        if (mode.isFpv()) {
            if (gamepad.pressed(Gamepad.DPAD_UP)) adjustUptilt(5);
            if (gamepad.pressed(Gamepad.DPAD_DOWN)) adjustUptilt(-5);
        }
    }

    private double hoverThrottle() {
        return 1.0 / Math.max(1.2, config.fpv.thrustToWeight);
    }

    private static double merge(double keyboard, double pad) {
        return DronePhysics.clamp(keyboard + pad, -1, 1);
    }

    private static double axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1 : 0) - (negative.isDown() ? 1 : 0);
    }

    // ------------------------------------------------------------------ actions

    private void cycleMode() {
        setMode(mode.nextInFamily());
    }

    private void switchDroneType() {
        setMode(mode.isFpv() ? lastCameraMode : lastFpvMode);
    }

    private void setMode(FlightMode next) {
        boolean enteringFpv = next.isFpv() && !mode.isFpv();
        mode = next;
        if (mode.isFpv()) lastFpvMode = mode;
        else lastCameraMode = mode;
        physics.onModeChanged(mode);
        if (enteringFpv) keyboardThrottle = physics.onGround ? 0 : hoverThrottle();
        zoomTarget = 1;
        showHint(mode.isFpv() ? "FPV: " + mode.displayName : "Camera drone: " + mode.displayName);
    }

    private void toggleRecording() {
        recording = !recording;
        showHint(recording ? "Recording" : "Recording stopped");
    }

    private void toggleOsd() {
        config.camera.showOsd = !config.camera.showOsd;
    }

    private void resetGimbal() {
        if (mode.isFpv()) return;
        physics.gimbalTarget = physics.gimbalTarget > -45 ? -90 : 0;
    }

    private void adjustUptilt(double delta) {
        config.camera.fpvUptilt = DronePhysics.clamp(config.camera.fpvUptilt + delta, 0, 60);
        showHint(String.format(Locale.ROOT, "Camera uptilt %.0f°", config.camera.fpvUptilt));
    }

    private void showHint(String text) {
        hint = text;
        hintTimer = 4;
    }

    // ------------------------------------------------------------------ outputs

    public Vector3d cameraPosition() {
        return cameraPos;
    }

    public Quaterniond cameraRotation() {
        return cameraRot;
    }

    public double verticalFov() {
        return verticalFov;
    }

    public boolean hideVanillaHud() {
        return flying && config.general.hideVanillaHud;
    }

    public void setOwnDroneId(int id) {
        ownDroneId = id;
    }

    public boolean shouldHide(Entity entity) {
        return flying && ownDroneId >= 0 && entity.getId() == ownDroneId;
    }

    public void renderHud(OsdCanvas canvas) {
        if (!flying) {
            if (hintTimer > 0) painterHintOnly(canvas);
            return;
        }
        if (config.camera.showOsd || physics.crashed) painter.paint(canvas, osd);
    }

    private void painterHintOnly(OsdCanvas c) {
        // After landing the hint (e.g. "Pilot took damage") fades out over the normal HUD.
        int alpha = (int) (Math.min(1, hintTimer) * 255) << 24;
        c.centeredText(hint, c.width() / 2, c.height() - 70, 0xFFFFFF | alpha, true);
        hintTimer = Math.max(0, hintTimer - 1 / 60.0);
    }

    private void updateOsd() {
        osd.mode = mode;
        osd.altitude = physics.altitude();
        osd.distanceHome = physics.horizontalDistanceHome();
        osd.distancePilot = physics.distanceToPilot;
        osd.horizontalSpeed = physics.horizontalSpeed();
        osd.verticalSpeed = physics.vel.y;
        osd.battery = physics.battery;
        osd.batteryEnabled = config.general.batteryMinutes > 0;
        osd.batteryMinutes = config.general.batteryMinutes;
        osd.voltage = physics.packVoltage();
        osd.flightTime = physics.flightTime;
        osd.signal = physics.signal;
        osd.throttle = physics.motor;
        osd.gimbal = physics.gimbal;
        osd.compass = physics.compassDegrees();
        osd.pitch = physics.pitchDegrees();
        osd.roll = physics.rollDegrees();
        osd.zoom = zoom;
        osd.verticalFov = verticalFov;
        double dx = pilotEye.x - physics.pos.x, dz = pilotEye.z - physics.pos.z;
        double toPilot = Math.atan2(-dx, -dz);
        double bearing = Math.toDegrees(physics.heading - toPilot);
        osd.homeBearing = ((bearing % 360) + 540) % 360 - 180;
        osd.recording = recording;
        osd.crashed = physics.crashed;
        osd.crashReason = physics.crashReason;
        osd.crashTimer = crashTimer;
        osd.maxRange = maxRange;
        osd.controller = gamepad.kind() == Gamepad.Kind.NONE ? "" : gamepad.name();
        osd.hint = hint;
        osd.hintTimer = hintTimer;
        osd.time = osdTime;
        osd.analogEffects = config.camera.analogEffects;
    }

    private DroneSound.Sample ownSoundSample() {
        if (!flying) return null;
        double motor = physics.motor;
        float volume = (float) (config.general.soundVolume * 0.35 * (0.25 + motor));
        float pitch = (float) (mode.isFpv() ? 0.8 + motor * 1.1 : 1.15 + motor * 0.5);
        return new DroneSound.Sample(physics.pos.x, physics.pos.y, physics.pos.z, volume, pitch);
    }

    private static net.minecraft.client.gui.screens.Screen screen(Minecraft mc) {
        //? if >=26.1 {
        return mc.gui.screen();
        //?} else
        //return mc.screen;
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
    }
}
