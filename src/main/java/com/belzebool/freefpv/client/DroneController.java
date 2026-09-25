package com.belzebool.freefpv.client;

import com.belzebool.freefpv.client.tools.DroneRemoteTool;
import com.belzebool.freefpv.client.tools.Multitool;
import com.belzebool.freefpv.client.tools.ToolSlot;
import com.belzebool.freefpv.compat.EmotecraftCompat;
import com.belzebool.freefpv.core.CameraShake;
import com.belzebool.freefpv.core.DroneConfig;
import com.belzebool.freefpv.core.DroneInput;
import com.belzebool.freefpv.core.DronePhysics;
import com.belzebool.freefpv.core.FlightMode;
import com.belzebool.freefpv.core.input.Gamepad;
import com.belzebool.freefpv.core.osd.OsdCanvas;
import com.belzebool.freefpv.core.osd.OsdPainter;
import com.belzebool.freefpv.core.osd.OsdState;
import com.belzebool.freefpv.net.DroneInfoPayload;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.platform.Platform;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//? if >=1.21.2 {
import net.minecraft.client.player.ClientInput;
//?} else
//import net.minecraft.client.player.Input;

/**
 * Client side of a flight: owns the physics, turns keyboard/mouse/controller into stick input, drives the camera,
 * OSD, sound, effects and network sync. Everything version-specific lives in the mixins and {@link GuiCanvas}.
 */
public final class DroneController {
    public static final DroneController INSTANCE = new DroneController();

    /** Near clipping plane while flying (vanilla uses 0.05); see {@code CameraMixin}. */
    public static final float NEAR_PLANE = 0.03f;
    /** Room kept between the lens and any block: more than the near-plane corners reach on the widest FPV lens. */
    private static final double CAMERA_CLEARANCE = 0.085;

    private final DronePhysics physics = new DronePhysics();
    private final DroneInput input = new DroneInput();
    private final Gamepad gamepad = new Gamepad();
    private final OsdState osd = new OsdState();
    private final OsdPainter painter = new OsdPainter();
    private final McDroneWorld world = new McDroneWorld();
    private final CameraShake shake = new CameraShake();
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
    private long hudFrameNanos;
    private double mouseX, mouseY;
    private double smoothYaw, smoothPitch;
    private double zoom = 1, zoomTarget = 1;
    private double keyboardThrottle;
    private double climbHold, descendHold;
    private double lastThrottle;
    private boolean lastThrottleAbsolute, lastAltitudeHold;
    private boolean recording;
    private double crashTimer;
    private boolean crashHandled;
    private double launchHold;
    private boolean launchLatch;
    private int ownDroneId = -1;
    private double maxRange = 256;
    private double verticalFov = 70;
    private String hint = "";
    private double hintTimer;
    private double osdTime;
    private boolean helpVisible;
    private double helpAutoTimer;
    private double helpAlpha;
    private int launches;
    private double exitFade;
    private double landedTimer;
    private boolean autoRthDone;
    private double beepTimer;
    private int lightColor = -1;
    private boolean wasOnGround = true;

    private final Vector3d cameraPos = new Vector3d();
    private final Vector3d center = new Vector3d();
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

    public DroneConfig config() {
        return config;
    }

    public void saveConfig() {
        config.save(configFile());
    }

    public boolean isFlying() {
        return flying;
    }

    /** First launch takes the family and mode from {@code general.startMode}; later ones remember the last used. */
    private void ensureModes() {
        if (!modeFromConfig) return;
        modeFromConfig = false;
        FlightMode start = FlightMode.parse(config.general.startMode, FlightMode.NORMAL);
        if (start.isFpv()) lastFpvMode = start;
        else lastCameraMode = start;
        mode = start;
    }

    /** Mode a drone of this family starts in; the tool slot shows and changes it. */
    public FlightMode launchMode(boolean fpv) {
        ensureModes();
        if (flying && mode.isFpv() == fpv) return mode;
        return fpv ? lastFpvMode : lastCameraMode;
    }

    /** Left click with a remote in the tool slot: next flight mode for the next launch. */
    public void cycleLaunchMode(boolean fpv) {
        ensureModes();
        if (fpv) lastFpvMode = lastFpvMode.nextInFamily();
        else lastCameraMode = lastCameraMode.nextInFamily();
        playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.3f);
    }

    private static boolean allowed(boolean fpv) {
        return ServerFeatures.allows(fpv ? DroneRemoteTool.FPV_ID : DroneRemoteTool.CAMERA_ID, false);
    }

    /** Launch key and gamepad: the drone of the remote in the tool slot, or the last flown type. */
    public void start(Minecraft mc) {
        Multitool tool = ToolSlot.INSTANCE.selected();
        ensureModes();
        start(mc, tool instanceof DroneRemoteTool remote ? remote.fpv() : mode.isFpv());
    }

    public void start(Minecraft mc, boolean fpv) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || flying) return;
        config = DroneConfig.load(configFile());
        if (!allowed(fpv)) {
            showHint(tr("hint.freefpv.not_allowed"));
            return;
        }
        gamepad.loadMappings(Platform.INSTANCE.configDir().resolve("freefpv").resolve("gamecontrollerdb.txt"));
        ensureModes();
        mode = fpv ? lastFpvMode : lastCameraMode;

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
        mouseX = mouseY = smoothYaw = smoothPitch = 0;
        zoom = zoomTarget = 1;
        keyboardThrottle = 0;
        climbHold = descendHold = 0;
        lastThrottle = 0;
        lastAltitudeHold = false;
        crashTimer = 0;
        crashHandled = false;
        osdTime = 0;
        ownDroneId = -1;
        exitFade = 0;
        landedTimer = 0;
        autoRthDone = false;
        beepTimer = 1;
        lightColor = -1;
        wasOnGround = true;
        shake.reset();
        helpVisible = config.controls.showHelpOnLaunch && launches < 2;
        helpAutoTimer = helpVisible ? 9 : 0;
        helpAlpha = 0;
        launches++;

        if (config.compat.useEmotecraft) EmotecraftCompat.start(config.compat.emote);
        mc.getSoundManager().play(new DroneSound(this::ownSoundSample));
        if (config.effects.windSound) mc.getSoundManager().play(new DroneSound(SoundEvents.ELYTRA_FLYING, this::windSample));
        playUi(fpv ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.35f);
        showHint(tr("hint.freefpv.launch", familyName() + ": " + mode.displayName, keyName(Keys.LAUNCH), keyName(Keys.HELP)));
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
        exitFade = config.camera.transitions ? 0.35 : 0;
        EmotecraftCompat.stop();
        restoreLightBar();
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
        restoreLightBar();
        if (savedCameraType != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(savedCameraType);
        }
    }

    // ------------------------------------------------------------------ ticks

    public void clientTick(Minecraft mc) {
        RemoteDrones.tick(mc, ownDroneId, config);
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
        while (Keys.RETURN_HOME.consumeClick()) toggleReturnHome();
        while (Keys.HELP.consumeClick()) toggleHelp();
        while (Keys.TOOL_SLOT.consumeClick()) {
        }

        if (!player.isAlive()) {
            stop(mc);
            return;
        }
        float health = player.getHealth();
        if (config.general.exitOnDamage && health < lastHealth - 0.01f) {
            stop(mc);
            showHint(tr("hint.freefpv.pilot_damage"));
            return;
        }
        lastHealth = health;

        if (physics.crashed && crashTimer > config.general.crashScreenSeconds) {
            stop(mc);
            return;
        }
        if (physics.autopilot == DronePhysics.Autopilot.LANDED && (landedTimer += 0.05) > 1.0) {
            stop(mc);
            showHint(tr("hint.freefpv.landed"));
            playUi(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 0.35f);
            return;
        }
        if (physics.autopilotAbort != DronePhysics.AutopilotAbort.NONE) {
            physics.autopilotAbort = DronePhysics.AutopilotAbort.NONE;
            showHint(tr("hint.freefpv.rth_blocked"));
            beep(0.8f);
        }
        if (!mode.isFpv() && config.cameraDrone.autoRthOnLowBattery && config.general.batteryMinutes > 0
            && physics.battery < 0.15 && !autoRthDone && physics.autopilot == DronePhysics.Autopilot.NONE && !physics.crashed) {
            autoRthDone = true;
            if (physics.startReturnHome(pilotEye, config.cameraDrone.rthHeight)) showHint(tr("hint.freefpv.rth_low_battery"));
        }

        if (config.effects.particles) {
            if (physics.crashed) {
                if (mode.isFpv() && crashTimer < 2.5) DroneEffects.crashSmoke(mc.level, physics.pos.x, physics.pos.y, physics.pos.z);
            } else {
                DroneEffects.propWash(mc.level, physics.pos.x, physics.pos.y, physics.pos.z, physics.motor, mode.isFpv());
                DroneEffects.rainOnProps(mc.level, physics.pos.x, physics.pos.y, physics.pos.z, physics.motor);
                if (physics.onGround != wasOnGround && physics.motor > 0.1) {
                    DroneEffects.groundPuff(mc.level, physics.pos.x, physics.pos.y, physics.pos.z);
                }
            }
        }
        wasOnGround = physics.onGround;
        beeps(0.05);
        updateLightBar();
        if (mode.isFpv() && !physics.crashed && physics.motor > 0.1) rumble(0, 0.04 + physics.motor * 0.1, 80);

        if (Platform.INSTANCE.canSendToServer(DroneStatePayload.TYPE)) {
            int flags = (physics.crashed ? DroneInfoPayload.CRASHED : 0)
                | (physics.autopilot != DronePhysics.Autopilot.NONE ? DroneInfoPayload.AUTOPILOT : 0);
            Platform.INSTANCE.sendToServer(new DroneStatePayload(true, mode.isFpv(),
                physics.pos.x, physics.pos.y, physics.pos.z,
                (float) physics.att.x, (float) physics.att.y, (float) physics.att.z, (float) physics.att.w,
                (float) physics.motor, flags));
        }
    }

    private void idleTick(Minecraft mc) {
        for (KeyMapping key : List.of(Keys.MODE, Keys.DRONE_TYPE, Keys.RECORD, Keys.OSD, Keys.GIMBAL_RESET, Keys.RETURN_HOME, Keys.HELP)) {
            while (key.consumeClick()) {
            }
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
        double renderRange = Math.max(32, (mc.options.getEffectiveRenderDistance() - 1) * 16);
        maxRange = Math.min(ServerFeatures.capRange(config.general.maxRange), renderRange);
        double alpha = physics.advance(dt, input, mode, config, world, pilotEye, maxRange);
        physics.cameraPose(alpha, mode, config, cameraPos, cameraRot);
        physics.prevPos.lerp(physics.pos, alpha, center);

        handleImpacts(mc);
        double speed = physics.vel.length();
        double buzz;
        if (mode.isFpv() && !physics.crashed) {
            buzz = 0.05 + 0.2 * physics.motor + Math.min(0.35, speed * 0.012);
        } else {
            buzz = physics.crashed ? 0 : 0.01 + Math.min(0.04, speed * 0.0025);
        }
        if (physics.onGround && physics.motor < 0.2) buzz *= 0.2;
        shake.update(dt, buzz);
        shake.apply(cameraRot, config.camera.shake);
        world.clampCamera(center, cameraPos, CAMERA_CLEARANCE);

        zoom += (zoomTarget - zoom) * (1 - Math.exp(-dt * 8));
        double aspect = Math.max(0.1, (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()));
        double horizontal = Math.toRadians(mode.isFpv() ? config.camera.fpvFov : config.camera.cameraDroneFov);
        if (!mode.isFpv() && config.camera.speedFov > 0) {
            double boost = mode == FlightMode.SPORT ? 0.10 : mode == FlightMode.NORMAL ? 0.04 : 0;
            horizontal *= 1 + config.camera.speedFov * boost * Math.min(1, physics.horizontalSpeed() / Math.max(1, config.speedFor(mode)));
        }
        double vertical = 2 * Math.atan(Math.tan(Math.min(horizontal, Math.toRadians(170)) / 2) / aspect);
        if (!mode.isFpv()) vertical = 2 * Math.atan(Math.tan(vertical / 2) / zoom);
        verticalFov = Math.max(10, Math.min(170, Math.toDegrees(vertical)));

        if (physics.crashed) crashTimer += dt;
        hintTimer = Math.max(0, hintTimer - dt);
        osdTime += dt;
        if (helpAutoTimer > 0 && (helpAutoTimer -= dt) <= 0) helpVisible = false;
        helpAlpha += ((helpVisible ? 1 : 0) - helpAlpha) * (1 - Math.exp(-dt * 8));
        updateOsd();
    }

    /** Sounds, debris, camera shake and rumble for knocks and crashes. */
    private void handleImpacts(Minecraft mc) {
        double impact = physics.takeImpact();
        float volume = (float) config.general.soundVolume;
        boolean particles = config.effects.particles && mc.level != null;
        if (physics.crashed && !crashHandled) {
            crashHandled = true;
            shake.addTrauma(1);
            rumble(1, 1, 450);
            if (particles) {
                if (physics.crashReason == DronePhysics.CrashReason.WATER) {
                    DroneEffects.splash(mc.level, physics.pos.x, physics.pos.y, physics.pos.z);
                } else {
                    DroneEffects.impact(mc.level, physics.impactPos, physics.impactDir, Math.max(impact, 8), true, volume);
                    DroneEffects.crashBurst(mc.level, physics.pos.x, physics.pos.y, physics.pos.z, mode.isFpv());
                }
            }
        } else if (impact > 1.2) {
            double scale = mode.isFpv() ? 1 : 0.4;
            shake.addTrauma(Math.min(0.8, impact / 10) * scale);
            rumble(Math.min(1, impact / 8), Math.min(1, impact / 12), 120);
            if (particles) DroneEffects.impact(mc.level, physics.impactPos, physics.impactDir, impact, false, volume);
        }
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
            input.altitudeHold = lastAltitudeHold;
            input.throttle = mode.isFpv() && !lastAltitudeHold ? lastThrottle : 0;
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
            // Cinematic mouse: the camera eases after the hand, most in Cine, barely in Sport.
            double tau = config.controls.mouseSmoothing * (mode == FlightMode.CINE ? 0.22 : mode == FlightMode.SPORT ? 0.03 : 0.07);
            smoothYaw += mouseYaw;
            smoothPitch += mousePitch;
            double k = tau <= 1e-3 ? 1 : 1 - Math.exp(-dt / tau);
            input.mouseYawDeg = smoothYaw * k;
            input.mousePitchDeg = smoothPitch * k;
            smoothYaw -= input.mouseYawDeg;
            smoothPitch -= input.mousePitchDeg;
        } else {
            input.pitch = forward;
            if (mode == FlightMode.ANGLE && config.controls.keyboardAltitudeHold) {
                // Space/Shift ask for a climb or descent; tap is gentle, holding ramps up to full power.
                climbHold = vertical > 0 ? climbHold + dt : 0;
                descendHold = vertical < 0 ? descendHold + dt : 0;
                double stick = 0;
                if (vertical > 0) stick = 0.3 + 0.7 * smooth(climbHold / 1.2);
                else if (vertical < 0) stick = -(0.45 + 0.55 * smooth(descendHold));
                input.throttle = stick;
                input.throttleAbsolute = false;
                input.altitudeHold = true;
                keyboardThrottle = physics.motor;
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
            }
            if (mode == FlightMode.ANGLE) {
                input.roll = strafe;
                double k = 1 - Math.exp(-dt / Math.max(1e-3, 0.03 * config.controls.mouseSmoothing));
                smoothYaw += mouseYaw;
                input.mouseYawDeg = smoothYaw * k;
                smoothYaw -= input.mouseYawDeg;
            } else {
                input.yaw = strafe;
                input.mousePitchDeg = mousePitch;
                input.mouseRollDeg = mouseYaw;
            }
        }

        if (pad) applyGamepad(mc, dt);

        if (physics.autopilot != DronePhysics.Autopilot.NONE && physics.autopilot != DronePhysics.Autopilot.LANDED
            && input.sticksActive(0.25)) {
            physics.cancelAutopilot();
            showHint(tr("hint.freefpv.rth_cancel"));
        }
        lastThrottle = input.throttle;
        lastThrottleAbsolute = input.throttleAbsolute;
        lastAltitudeHold = input.altitudeHold;
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
            input.altitudeHold = false;
            keyboardThrottle = input.throttle;
        } else if (gamepad.recentlyUsed(1.5)) {
            input.throttle = throttle;
            input.throttleAbsolute = false;
            input.altitudeHold = mode == FlightMode.ANGLE;
            keyboardThrottle = physics.motor;
        }

        if (rc) return;
        if (gamepad.pressed(Gamepad.START)) stop(mc);
        if (gamepad.pressed(Gamepad.Y)) cycleMode();
        if (gamepad.pressed(Gamepad.BACK)) switchDroneType();
        if (gamepad.pressed(Gamepad.A)) toggleRecording();
        if (gamepad.pressed(Gamepad.X)) toggleOsd();
        if (gamepad.pressed(Gamepad.B)) resetGimbal();
        if (gamepad.pressed(Gamepad.DPAD_LEFT)) toggleReturnHome();
        if (gamepad.pressed(Gamepad.DPAD_RIGHT)) toggleHelp();
        if (mode.isFpv()) {
            if (gamepad.pressed(Gamepad.DPAD_UP)) adjustUptilt(5);
            if (gamepad.pressed(Gamepad.DPAD_DOWN)) adjustUptilt(-5);
        }
    }

    private double hoverThrottle() {
        return 1.0 / Math.max(1.2, config.fpv.thrustToWeight);
    }

    private static double smooth(double t) {
        t = DronePhysics.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
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
        if (!allowed(!mode.isFpv())) {
            showHint(tr("hint.freefpv.not_allowed"));
            return;
        }
        setMode(mode.isFpv() ? lastCameraMode : lastFpvMode);
    }

    private void setMode(FlightMode next) {
        boolean enteringFpv = next.isFpv() && !mode.isFpv();
        mode = next;
        if (mode.isFpv()) lastFpvMode = mode;
        else lastCameraMode = mode;
        physics.cancelAutopilot();
        physics.onModeChanged(mode);
        if (enteringFpv) keyboardThrottle = physics.onGround ? 0 : hoverThrottle();
        zoomTarget = 1;
        smoothYaw = smoothPitch = 0;
        showHint(familyName() + ": " + mode.displayName);
        playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.25f);
    }

    private String familyName() {
        return tr(mode.isFpv() ? "hint.freefpv.fpv" : "hint.freefpv.camera");
    }

    private void toggleRecording() {
        recording = !recording;
        showHint(tr(recording ? "hint.freefpv.recording" : "hint.freefpv.recording_stopped"));
    }

    private void toggleOsd() {
        config.camera.showOsd = !config.camera.showOsd;
    }

    private void toggleHelp() {
        helpVisible = !helpVisible;
        helpAutoTimer = 0;
    }

    private void resetGimbal() {
        if (mode.isFpv()) return;
        physics.gimbalTarget = physics.gimbalTarget > -45 ? -90 : 0;
    }

    private void toggleReturnHome() {
        if (mode.isFpv()) {
            showHint(tr("hint.freefpv.rth_fpv"));
            return;
        }
        if (physics.autopilot != DronePhysics.Autopilot.NONE) {
            physics.cancelAutopilot();
            showHint(tr("hint.freefpv.rth_cancel"));
            return;
        }
        if (physics.startReturnHome(pilotEye, config.cameraDrone.rthHeight)) {
            showHint(tr("hint.freefpv.rth", keyName(Keys.RETURN_HOME)));
            playUi(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.35f);
        }
    }

    private void adjustUptilt(double delta) {
        config.camera.fpvUptilt = DronePhysics.clamp(config.camera.fpvUptilt + delta, 0, 60);
        showHint(tr("hint.freefpv.uptilt", String.format(Locale.ROOT, "%.0f", config.camera.fpvUptilt)));
    }

    private void showHint(String text) {
        hint = text;
        hintTimer = 4;
    }

    // ------------------------------------------------------------------ feedback

    /** Low battery and weak signal beeps: a DJI chime on the camera drone, a buzzer on the FPV quad. */
    private void beeps(double dt) {
        if (!config.general.beeps || physics.crashed) return;
        beepTimer -= dt;
        if (beepTimer > 0) return;
        boolean battery = config.general.batteryMinutes > 0;
        if (battery && physics.battery < 0.1) {
            beep(1.2f);
            beepTimer = 1.2;
        } else if (battery && physics.battery < 0.2) {
            beep(1.0f);
            beepTimer = 4;
        } else if (physics.signal < 0.35) {
            beep(0.8f);
            beepTimer = 3;
        } else {
            beepTimer = 0.5;
        }
    }

    private void beep(float pitch) {
        playUi(mode.isFpv() ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.NOTE_BLOCK_PLING.value(), pitch * 1.4f, 0.3f);
    }

    private void playUi(SoundEvent sound, float pitch, float volume) {
        if (!config.general.beeps) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume * (float) config.general.soundVolume));
    }

    private void rumble(double low, double high, int millis) {
        if (config.gamepad.rumble && config.gamepad.enabled && gamepad.kind() == Gamepad.Kind.GAMEPAD) gamepad.rumble(low, high, millis);
    }

    /** DualSense / DualShock light bar: colour by flight mode, orange blink on low battery, red after a crash. */
    private void updateLightBar() {
        if (!config.gamepad.lightBar || gamepad.kind() != Gamepad.Kind.GAMEPAD) return;
        int color = switch (mode) {
            case ACRO -> 0xFF2020;
            case ANGLE -> 0x20FF40;
            case SPORT -> 0xFF8000;
            case CINE -> 0x4080FF;
            default -> 0xFFFFFF;
        };
        if (config.general.batteryMinutes > 0 && physics.battery < 0.15 && ((int) (osdTime * 2)) % 2 == 0) color = 0xFF4000;
        if (physics.crashed) color = 0xFF0000;
        if (color != lightColor) {
            gamepad.light(color);
            lightColor = color;
        }
    }

    private void restoreLightBar() {
        if (lightColor != -1 && gamepad.kind() == Gamepad.Kind.GAMEPAD) gamepad.light(0x0040FF);
        lightColor = -1;
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

    public void onDroneInfo(DroneInfoPayload payload) {
        RemoteDrones.onInfo(payload);
    }

    public boolean shouldHide(Entity entity) {
        return flying && ownDroneId >= 0 && entity.getId() == ownDroneId;
    }

    /** 0 = the entity is not our pilot, 1 = flying the camera drone, 2 = flying FPV. */
    public int localPilotMode(int entityId) {
        return flying && pilot != null && pilot.getId() == entityId ? (mode.isFpv() ? 2 : 1) : 0;
    }

    public void renderHud(OsdCanvas canvas) {
        long now = System.nanoTime();
        double dt = hudFrameNanos == 0 ? 0 : Math.min(0.1, (now - hudFrameNanos) / 1e9);
        hudFrameNanos = now;
        if (!flying) {
            if (exitFade > 0) {
                canvas.fill(0, 0, canvas.width(), canvas.height(), (int) (Math.min(1, exitFade / 0.35) * 255) << 24);
                exitFade -= dt;
            }
            if (hintTimer > 0) painterHintOnly(canvas, dt);
            return;
        }
        painter.paint(canvas, osd);
    }

    private void painterHintOnly(OsdCanvas c, double dt) {
        // After landing the hint (e.g. "Pilot took damage") fades out over the normal HUD.
        int alpha = (int) (Math.min(1, hintTimer) * 255) << 24;
        c.centeredText(hint, c.width() / 2, c.height() - 70, 0xFFFFFF | alpha, true);
        hintTimer = Math.max(0, hintTimer - dt);
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
        osd.showOsd = config.camera.showOsd;
        osd.hoverThrottle = hoverThrottle();
        osd.altitudeHold = input.altitudeHold;
        osd.autopilot = physics.autopilot;
        osd.feedAge = osdTime;
        osd.transitions = config.camera.transitions;
        osd.connectingText = tr("osd.freefpv.connecting");
        osd.helpAlpha = helpAlpha;
        if (helpAlpha > 0.01) {
            osd.helpTitle = tr(mode.isFpv() ? "help.freefpv.title_fpv" : "help.freefpv.title_camera", mode.displayName);
            osd.help = helpRows();
        }
    }

    /** The controls panel for the current mode, built from the actual key bindings. */
    private List<String[]> helpRows() {
        Options o = Minecraft.getInstance().options;
        List<String[]> rows = new ArrayList<>();
        String wasd = keyName(o.keyUp) + " " + keyName(o.keyLeft) + " " + keyName(o.keyDown) + " " + keyName(o.keyRight);
        String upDown = keyName(o.keyJump) + " / " + keyName(o.keyShift);
        String mouse = tr("help.freefpv.mouse");
        if (!mode.isFpv()) {
            rows.add(row(wasd, "help.freefpv.fly"));
            rows.add(row(upDown, "help.freefpv.updown"));
            rows.add(row(mouse, "help.freefpv.look"));
            rows.add(row(tr("help.freefpv.wheel"), "help.freefpv.zoom"));
            rows.add(row(keyName(Keys.MODE), "help.freefpv.modes_camera"));
            rows.add(row(keyName(Keys.GIMBAL_RESET), "help.freefpv.gimbal"));
            rows.add(row(keyName(Keys.RETURN_HOME), "help.freefpv.rth"));
            rows.add(row(keyName(Keys.DRONE_TYPE), "help.freefpv.to_fpv"));
        } else if (mode == FlightMode.ANGLE) {
            rows.add(row(wasd, "help.freefpv.tilt"));
            rows.add(row(mouse, "help.freefpv.turn"));
            rows.add(row(upDown, config.controls.keyboardAltitudeHold ? "help.freefpv.climb" : "help.freefpv.throttle"));
            rows.add(row(keyName(Keys.MODE), "help.freefpv.modes_fpv"));
            rows.add(row(keyName(Keys.DRONE_TYPE), "help.freefpv.to_camera"));
        } else {
            rows.add(row(mouse, "help.freefpv.pitch_roll"));
            rows.add(row(keyName(o.keyUp) + " / " + keyName(o.keyDown), "help.freefpv.pitch"));
            rows.add(row(keyName(o.keyLeft) + " / " + keyName(o.keyRight), "help.freefpv.yaw"));
            rows.add(row(upDown, "help.freefpv.throttle"));
            rows.add(row(keyName(Keys.MODE), "help.freefpv.modes_fpv"));
            rows.add(row(keyName(Keys.DRONE_TYPE), "help.freefpv.to_camera"));
        }
        rows.add(row(keyName(Keys.LAUNCH), "help.freefpv.land"));
        rows.add(row(keyName(Keys.HELP), "help.freefpv.help"));
        return rows;
    }

    private static String[] row(String key, String actionKey) {
        return new String[]{key, tr(actionKey)};
    }

    private DroneSound.Sample ownSoundSample() {
        if (!flying) return null;
        double motor = physics.motor;
        float volume = (float) (config.general.soundVolume * 0.35 * (0.25 + motor));
        float pitch = (float) (mode.isFpv() ? 0.8 + motor * 1.1 : 1.15 + motor * 0.5);
        return new DroneSound.Sample(physics.pos.x, physics.pos.y, physics.pos.z, volume, pitch);
    }

    /** Wind rush in the FPV camera, growing with airspeed; barely there on the camera drone. */
    private DroneSound.Sample windSample() {
        if (!flying) return null;
        double speed = physics.crashed ? 0 : physics.vel.length();
        double k = mode.isFpv() ? DronePhysics.clamp((speed - 6) / 22, 0, 1) : DronePhysics.clamp((speed - 10) / 20, 0, 1) * 0.3;
        return new DroneSound.Sample(cameraPos.x, cameraPos.y, cameraPos.z,
            (float) (config.general.soundVolume * 0.8 * k * k), (float) (0.8 + k * 0.5));
    }

    private static net.minecraft.client.gui.screens.Screen screen(Minecraft mc) {
        return Compat.screen(mc);
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
