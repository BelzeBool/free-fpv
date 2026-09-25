package com.belzebool.freefpv.core;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Flight model, independent of Minecraft. 1 block = 1 metre, time in seconds.
 * <p>
 * Body frame follows the camera convention: -Z forward, +Y up, +X right. {@link #att} rotates body into world.
 * Physics runs at a fixed {@link #STEP}; rendering interpolates between the last two sub-steps.
 */
public final class DronePhysics {
    public static final double STEP = 1.0 / 240.0;
    public static final double GRAVITY = 9.81;
    private static final double MAX_FRAME = 0.1;

    public enum CrashReason { NONE, IMPACT, WATER, SIGNAL_LOST, BATTERY }

    /** Camera drone autopilot, like DJI return to home: climb, turn and fly back, then land by the pilot. */
    public enum Autopilot { NONE, RTH_CLIMB, RTH_CRUISE, RTH_DESCEND, LANDED }

    public enum AutopilotAbort { NONE, OBSTACLE }

    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();
    public final Vector3d vel = new Vector3d();
    public final Quaterniond att = new Quaterniond();
    public final Quaterniond prevAtt = new Quaterniond();
    /** Body angular rates, rad/s (x = pitch, y = yaw, z = roll). */
    public final Vector3d rates = new Vector3d();
    public final Vector3d home = new Vector3d();
    /** Collision box half extents. Tall enough that the camera always has room between the box and a wall. */
    public final Vector3d half = new Vector3d(0.16, 0.09, 0.16);

    /** Yaw of the airframe around world up, radians; 0 faces north (-Z), positive turns left. */
    public double heading;
    private double prevHeading;
    private double yawVelocity;
    private double tiltForward;
    private double tiltRight;
    /** Camera gimbal tilt in degrees, negative looks down (camera drones only). */
    public double gimbal;
    private double prevGimbal;
    public double gimbalTarget;

    /** Motor output 0..1. */
    public double motor;
    public double battery = 1;
    public double flightTime;
    public double signal = 1;
    public double distanceToPilot;
    public boolean onGround;
    public boolean crashed;
    public CrashReason crashReason = CrashReason.NONE;
    public double lastImpactSpeed;

    public Autopilot autopilot = Autopilot.NONE;
    public AutopilotAbort autopilotAbort = AutopilotAbort.NONE;
    private final Vector3d rthTarget = new Vector3d();
    private double rthAltitude;
    private double rthBestDistance;
    private double rthStallTimer;

    /** Strongest impact since {@link #takeImpact()} was last called; drives sounds, particles, rumble and shake. */
    private double pendingImpact;
    public final Vector3d impactPos = new Vector3d();
    /** Direction from the drone into the surface it hit. */
    public final Vector3d impactDir = new Vector3d();

    /** Distance to whatever is below, probed a few times a second while altitude hold is on (up to 4 blocks). */
    private double groundDistance = 4;
    private int groundProbe;
    private final Vector3d probe = new Vector3d();
    private final Vector3d probeResult = new Vector3d();

    private double accumulator;
    private final Random random = new Random();
    private final Vector3d tmp = new Vector3d();
    private final Vector3d motion = new Vector3d();
    private final Vector3d allowed = new Vector3d();

    public void reset(double x, double y, double z, double heading, FlightMode mode) {
        pos.set(x, y, z);
        prevPos.set(pos);
        home.set(pos);
        vel.zero();
        rates.zero();
        groundDistance = 4;
        this.heading = heading;
        prevHeading = heading;
        yawVelocity = 0;
        tiltForward = tiltRight = 0;
        att.identity().rotateY(heading);
        prevAtt.set(att);
        gimbal = prevGimbal = gimbalTarget = mode.isFpv() ? 0 : -10;
        motor = 0;
        battery = 1;
        flightTime = 0;
        signal = 1;
        onGround = true;
        crashed = false;
        crashReason = CrashReason.NONE;
        lastImpactSpeed = 0;
        pendingImpact = 0;
        autopilot = Autopilot.NONE;
        autopilotAbort = AutopilotAbort.NONE;
        accumulator = 0;
    }

    /**
     * Starts return to home: climb to a safe height, turn toward the pilot, fly back and land a couple of blocks in
     * front of them. Stick input cancels it. Returns false when the drone can't fly home (crashed, FPV).
     */
    public boolean startReturnHome(Vector3d pilot, double rthHeight) {
        if (crashed) return false;
        double dx = pos.x - pilot.x, dz = pos.z - pilot.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double k = d > 1e-3 ? Math.min(1, 2.5 / d) : 0;
        rthTarget.set(pilot.x + dx * k, pilot.y, pilot.z + dz * k);
        rthAltitude = Math.max(pos.y, pilot.y + rthHeight);
        rthBestDistance = d;
        rthStallTimer = 0;
        autopilotAbort = AutopilotAbort.NONE;
        autopilot = d < 3.5 ? Autopilot.RTH_DESCEND : Autopilot.RTH_CLIMB;
        return true;
    }

    public void cancelAutopilot() {
        if (autopilot != Autopilot.LANDED) autopilot = Autopilot.NONE;
    }

    /** Returns the strongest impact speed since the last call and clears it. */
    public double takeImpact() {
        double v = pendingImpact;
        pendingImpact = 0;
        return v;
    }

    /** Syncs derived state when the pilot switches modes mid-air. */
    public void onModeChanged(FlightMode mode) {
        heading = headingOf(att);
        prevHeading = heading;
        yawVelocity = 0;
        if (!mode.isFpv()) {
            tiltForward = tiltRight = 0;
            rates.zero();
        }
    }

    /**
     * Advances the simulation by a real frame time. Returns the interpolation factor for rendering.
     */
    public double advance(double frameSeconds, DroneInput in, FlightMode mode, DroneConfig cfg, DroneWorld world, Vector3d pilot, double maxRange) {
        applyMouse(in, mode, cfg);
        accumulator += Math.min(frameSeconds, MAX_FRAME);
        while (accumulator >= STEP) {
            accumulator -= STEP;
            substep(STEP, in, mode, cfg, world, pilot, maxRange);
        }
        return accumulator / STEP;
    }

    private void applyMouse(DroneInput in, FlightMode mode, DroneConfig cfg) {
        if (crashed) return;
        if (!mode.isFpv()) {
            // The autopilot steers while returning home; the mouse still tilts the gimbal.
            if (autopilot == Autopilot.NONE) {
                heading -= Math.toRadians(in.mouseYawDeg);
                prevHeading -= Math.toRadians(in.mouseYawDeg);
            }
            gimbalTarget = clamp(gimbalTarget + in.mousePitchDeg, cfg.cameraDrone.gimbalMin, cfg.cameraDrone.gimbalMax);
        } else if (mode == FlightMode.ANGLE) {
            heading -= Math.toRadians(in.mouseYawDeg);
        } else if (in.mouseYawDeg != 0 || in.mousePitchDeg != 0 || in.mouseRollDeg != 0) {
            Quaterniond delta = new Quaterniond().rotateXYZ(
                Math.toRadians(in.mousePitchDeg),
                -Math.toRadians(in.mouseYawDeg),
                -Math.toRadians(in.mouseRollDeg));
            att.mul(delta).normalize();
            prevAtt.mul(delta).normalize();
        }
    }

    private void substep(double dt, DroneInput in, FlightMode mode, DroneConfig cfg, DroneWorld world, Vector3d pilot, double maxRange) {
        prevPos.set(pos);
        prevAtt.set(att);
        prevHeading = heading;
        prevGimbal = gimbal;

        distanceToPilot = Math.sqrt(sq(pos.x - pilot.x) + sq(pos.z - pilot.z) + sq(pos.y - pilot.y));
        signal = 1 - smoothstep(maxRange * 0.75, maxRange, distanceToPilot);

        boolean motorsOn = !crashed;
        if (motorsOn) {
            flightTime += dt;
            drainBattery(dt, mode, cfg);
            if (mode.isFpv() && distanceToPilot > maxRange * 1.08) crash(CrashReason.SIGNAL_LOST);
            if (battery <= 0 && cfg.general.batteryMinutes > 0) crash(CrashReason.BATTERY);
            motorsOn = !crashed;
        }

        if (!motorsOn) {
            fallStep(dt);
        } else if (mode.isFpv()) {
            if (in.altitudeHold && mode == FlightMode.ANGLE && (groundProbe++ & 7) == 0) {
                world.collide(pos, half, probe.set(0, -4, 0), probeResult);
                groundDistance = -probeResult.y;
            }
            fpvStep(dt, in, mode, cfg);
        } else {
            cameraStep(dt, in, mode, cfg, pilot, maxRange);
        }

        moveAndCollide(dt, mode, cfg, world);

        if (!crashed && world.isInLiquid(pos.x, pos.y, pos.z)) crash(CrashReason.WATER);
    }

    // ---------------------------------------------------------------- camera drone (DJI-like)

    private void cameraStep(double dt, DroneInput in, FlightMode mode, DroneConfig cfg, Vector3d pilot, double maxRange) {
        double maxSpeed = cfg.speedFor(mode);
        double climb = cfg.climbFor(mode);
        double accel = cfg.accelFor(mode);
        double yawRate = Math.toRadians(cfg.yawRateFor(mode));
        double smoothing = mode == FlightMode.CINE ? 3 : mode == FlightMode.SPORT ? 10 : 6;

        double targetX, targetZ, targetY;
        if (autopilot != Autopilot.NONE) {
            accel = Math.max(accel, cfg.cameraDrone.normalAccel);
            // Home is flown at Normal speed whatever the mode, so Cine doesn't take forever.
            double speed = Math.max(maxSpeed, cfg.cameraDrone.normalSpeed);
            double rate = Math.max(yawRate, Math.toRadians(cfg.cameraDrone.normalYawRate));
            autopilotStep(dt, speed, Math.max(climb, cfg.cameraDrone.normalClimb), rate);
            yawVelocity = 0;
            targetX = autopilotVel.x;
            targetY = autopilotVel.y;
            targetZ = autopilotVel.z;
        } else {
            yawVelocity += (in.yaw * yawRate - yawVelocity) * (1 - Math.exp(-dt * smoothing));
            heading -= yawVelocity * dt;
            double fx = -Math.sin(heading), fz = -Math.cos(heading);
            double rx = Math.cos(heading), rz = -Math.sin(heading);
            targetX = (fx * in.pitch + rx * in.roll) * maxSpeed;
            targetZ = (fz * in.pitch + rz * in.roll) * maxSpeed;
            targetY = in.throttle * climb;
        }
        double fx = -Math.sin(heading), fz = -Math.cos(heading);
        double rx = Math.cos(heading), rz = -Math.sin(heading);

        if (cfg.general.batteryMinutes > 0 && battery < 0.05) targetY = Math.min(targetY, -1.2);

        // Geofence: at the range limit the drone refuses to fly further away, like DJI max distance.
        double dx = pos.x - pilot.x, dz = pos.z - pilot.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (distanceToPilot > maxRange && horizontal > 1e-3) {
            double nx = dx / horizontal, nz = dz / horizontal;
            double outward = targetX * nx + targetZ * nz;
            double push = Math.min(2.0, (distanceToPilot - maxRange) * 0.8);
            if (outward > -push) {
                targetX -= (outward + push) * nx;
                targetZ -= (outward + push) * nz;
            }
            if (pos.y - pilot.y > 0 && targetY > 0) targetY = 0;
        }

        double dvx = targetX - vel.x, dvz = targetZ - vel.z;
        double dvh = Math.sqrt(dvx * dvx + dvz * dvz);
        boolean braking = targetX * targetX + targetZ * targetZ < vel.x * vel.x + vel.z * vel.z;
        double maxDv = accel * (braking ? 1.6 : 1.0) * dt;
        if (dvh > maxDv) {
            dvx *= maxDv / dvh;
            dvz *= maxDv / dvh;
        }
        double dvy = clamp(targetY - vel.y, -accel * dt, accel * dt);
        vel.add(dvx, dvy, dvz);

        // Airframe lean follows acceleration and airspeed; the gimbal keeps the picture level.
        double aForward = (dvx * fx + dvz * fz) / dt;
        double aRight = (dvx * rx + dvz * rz) / dt;
        double vForward = vel.x * fx + vel.z * fz;
        double vRight = vel.x * rx + vel.z * rz;
        double maxTilt = Math.toRadians(mode == FlightMode.SPORT ? 35 : 25);
        double wantForward = clamp(Math.atan2(aForward, GRAVITY) + vForward * 0.025, -maxTilt, maxTilt);
        double wantRight = clamp(Math.atan2(aRight, GRAVITY) + vRight * 0.025, -maxTilt, maxTilt);
        double tiltBlend = 1 - Math.exp(-dt * 8);
        tiltForward += (wantForward - tiltForward) * tiltBlend;
        tiltRight += (wantRight - tiltRight) * tiltBlend;
        att.identity().rotateY(heading).rotateX(-tiltForward).rotateZ(-tiltRight);

        gimbalTarget = clamp(gimbalTarget + in.gimbal * cfg.cameraDrone.gimbalSpeed * dt, cfg.cameraDrone.gimbalMin, cfg.cameraDrone.gimbalMax);
        gimbal += (gimbalTarget - gimbal) * (1 - Math.exp(-dt * (mode == FlightMode.CINE ? 4 : 10)));

        double hoverLoad = 0.45 + 0.1 * Math.min(1, vel.length() / Math.max(1, maxSpeed));
        motor += (hoverLoad - motor) * (1 - Math.exp(-dt * 10));
        if (onGround && targetY <= 0 && vel.lengthSquared() < 0.01) motor += (0.15 - motor) * (1 - Math.exp(-dt * 6));
    }

    private final Vector3d autopilotVel = new Vector3d();

    /** Works out the velocity the return-to-home autopilot wants this step, in world space. */
    private void autopilotStep(double dt, double maxSpeed, double climb, double yawRate) {
        double dx = rthTarget.x - pos.x, dz = rthTarget.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        autopilotVel.zero();
        switch (autopilot) {
            case RTH_CLIMB -> {
                autopilotVel.y = climb;
                if (pos.y >= rthAltitude - 0.3) {
                    autopilot = Autopilot.RTH_CRUISE;
                } else if (vel.y < climb * 0.3) {
                    // Something above: cruise home at the current height instead.
                    rthStallTimer += dt;
                    if (rthStallTimer > 1.2) {
                        rthAltitude = pos.y;
                        rthStallTimer = 0;
                        autopilot = Autopilot.RTH_CRUISE;
                    }
                } else {
                    rthStallTimer = 0;
                }
            }
            case RTH_CRUISE -> {
                double want = Math.atan2(-dx, -dz);
                double diff = wrapAngle(want - heading);
                heading += clamp(diff, -yawRate * dt, yawRate * dt);
                double align = clamp(1 - Math.abs(diff) / 1.2, 0, 1);
                double speed = Math.min(maxSpeed, 0.8 * dist + 0.4) * align;
                if (dist > 1e-3) {
                    autopilotVel.x = dx / dist * speed;
                    autopilotVel.z = dz / dist * speed;
                }
                autopilotVel.y = clamp((rthAltitude - pos.y) * 1.5, -climb, climb);
                if (dist < 0.6) {
                    autopilot = Autopilot.RTH_DESCEND;
                } else if (dist < rthBestDistance - 0.4) {
                    rthBestDistance = dist;
                    rthStallTimer = 0;
                } else if ((rthStallTimer += dt) > 4) {
                    autopilot = Autopilot.NONE;
                    autopilotAbort = AutopilotAbort.OBSTACLE;
                }
            }
            case RTH_DESCEND -> {
                autopilotVel.x = clamp(dx * 1.2, -1, 1);
                autopilotVel.z = clamp(dz * 1.2, -1, 1);
                autopilotVel.y = -Math.min(climb, 2.2);
                if (onGround) autopilot = Autopilot.LANDED;
            }
            case LANDED -> autopilotVel.y = -0.5;
            default -> {
            }
        }
    }

    private static double wrapAngle(double a) {
        a %= Math.PI * 2;
        if (a > Math.PI) a -= Math.PI * 2;
        if (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    // ---------------------------------------------------------------- FPV quad

    private void fpvStep(double dt, DroneInput in, FlightMode mode, DroneConfig cfg) {
        Fpv f = new Fpv(cfg.fpv);
        double hover = 1.0 / Math.max(1.2, f.twr);

        if (mode == FlightMode.ANGLE) {
            double yawRate = Math.toRadians(actualRate(in.yaw, f.yawCenter, f.yawMax, f.expo));
            heading -= yawRate * dt;
            double maxAngle = Math.toRadians(f.maxAngle);
            Quaterniond target = new Quaterniond().rotateY(heading).rotateX(-in.pitch * maxAngle).rotateZ(-in.roll * maxAngle);
            att.slerp(target, 1 - Math.exp(-dt * 14)).normalize();
            rates.zero();
        } else {
            double wantX = -Math.toRadians(actualRate(in.pitch, f.center, f.max, f.expo));
            double wantY = -Math.toRadians(actualRate(in.yaw, f.yawCenter, f.yawMax, f.expo));
            double wantZ = -Math.toRadians(actualRate(in.roll, f.center, f.max, f.expo));
            double blend = 1 - Math.exp(-dt / 0.03);
            rates.x += (wantX - rates.x) * blend;
            rates.y += (wantY - rates.y) * blend;
            rates.z += (wantZ - rates.z) * blend;
            integrateRates(dt);
            heading = headingOf(att);
        }

        double command;
        if (in.throttleAbsolute) {
            command = clamp(in.throttle, 0, 1);
        } else if (in.altitudeHold && mode == FlightMode.ANGLE) {
            // The stick asks for a climb rate; centred holds height, full up punches out.
            double climbTarget = in.throttle >= 0 ? in.throttle * 18 : in.throttle * 8;
            // Soft landing: the closer the ground, the slower the descent, so holding Shift never slams it in.
            climbTarget = Math.max(climbTarget, -(0.8 + groundDistance * 2.2));
            if (onGround && climbTarget <= 0.1) {
                command = 0.04;
            } else {
                command = clamp(hover * (1 + 2.5 * (climbTarget - vel.y) / GRAVITY), 0.02, 1);
            }
        } else {
            command = in.throttle >= 0 ? hover + in.throttle * (1 - hover) : hover * (1 + in.throttle);
        }
        if (mode == FlightMode.ANGLE) {
            double upY = upY(att);
            if (upY > 0.3) command = Math.min(1, command / upY);
        }
        motor += (command - motor) * (1 - Math.exp(-dt / 0.035));

        if (onGround && motor < hover * 0.6) {
            // Sitting on the ground: settle flat instead of spinning in place.
            rates.mul(Math.exp(-dt * 20));
            Quaterniond level = new Quaterniond().rotateY(headingOf(att));
            att.slerp(level, 1 - Math.exp(-dt * 10)).normalize();
        }

        double thrust = motor * f.twr * GRAVITY;
        Vector3d up = att.transform(tmp.set(0, 1, 0));
        double speed = vel.length();
        double drag = f.linearDrag + f.quadraticDrag * speed;
        vel.x += (up.x * thrust - vel.x * drag) * dt;
        vel.y += (up.y * thrust - GRAVITY - vel.y * drag) * dt;
        vel.z += (up.z * thrust - vel.z * drag) * dt;
    }

    private record Fpv(double twr, double maxAngle, double center, double max, double expo,
                       double yawCenter, double yawMax, double linearDrag, double quadraticDrag) {
        Fpv(DroneConfig.Fpv c) {
            this(c.thrustToWeight, c.maxAngle, c.centerRate, c.maxRate, c.expo, c.yawCenterRate, c.yawMaxRate, c.linearDrag, c.quadraticDrag);
        }
    }

    // ---------------------------------------------------------------- unpowered

    private void fallStep(double dt) {
        motor += (0 - motor) * (1 - Math.exp(-dt * 12));
        integrateRates(dt);
        rates.mul(Math.exp(-dt * (onGround ? 8 : 0.6)));
        double speed = vel.length();
        double drag = 0.1 + 0.02 * speed;
        vel.x -= vel.x * drag * dt;
        vel.y -= (GRAVITY + vel.y * drag) * dt;
        vel.z -= vel.z * drag * dt;
        heading = headingOf(att);
    }

    // ---------------------------------------------------------------- shared

    private void moveAndCollide(double dt, FlightMode mode, DroneConfig cfg, DroneWorld world) {
        motion.set(vel).mul(dt);
        if (!world.isLoaded(pos.x + motion.x, pos.y + motion.y, pos.z + motion.z)) {
            // Terrain the client hasn't loaded is a wall, otherwise the drone would fall into the void.
            vel.zero();
            return;
        }
        world.collide(pos, half, motion, allowed);

        boolean wasOnGround = onGround;
        onGround = false;
        double impact = 0;
        boolean camera = !mode.isFpv();
        double restitution = camera ? 0 : 0.3;
        tmp.zero();
        if (Math.abs(allowed.x - motion.x) > 1e-7) {
            impact = Math.max(impact, Math.abs(vel.x));
            tmp.x = Math.signum(motion.x);
            vel.x = -vel.x * restitution;
        }
        if (Math.abs(allowed.z - motion.z) > 1e-7) {
            if (Math.abs(vel.z) > impact) tmp.set(0, 0, Math.signum(motion.z));
            impact = Math.max(impact, Math.abs(vel.z));
            vel.z = -vel.z * restitution;
        }
        if (Math.abs(allowed.y - motion.y) > 1e-7) {
            if (motion.y < 0) onGround = true;
            // Settling onto the ground at low speed is a landing, not an impact.
            double vy = Math.abs(vel.y);
            if (!(motion.y < 0 && (wasOnGround || vy < 2.5))) {
                if (vy > impact) tmp.set(0, Math.signum(motion.y), 0);
                impact = Math.max(impact, vy);
            }
            vel.y = motion.y < 0 ? 0 : -vel.y * restitution;
        }
        if (impact > pendingImpact) {
            pendingImpact = impact;
            impactPos.set(pos).add(allowed);
            impactDir.set(tmp);
        }
        pos.add(allowed);

        if (onGround) {
            double friction = Math.exp(-dt * (crashed ? 4 : 7));
            vel.x *= friction;
            vel.z *= friction;
        }

        lastImpactSpeed = impact;
        if (!crashed && impact > 0) {
            boolean breaks = camera ? !cfg.cameraDrone.obstacleAvoidance && impact > cfg.fpv.crashSpeed * 1.3
                : impact > cfg.fpv.crashSpeed;
            if (breaks) crash(CrashReason.IMPACT);
        }
    }

    private void drainBattery(double dt, FlightMode mode, DroneConfig cfg) {
        double minutes = cfg.general.batteryMinutes;
        if (minutes <= 0) {
            battery = 1;
            return;
        }
        double load;
        if (mode.isFpv()) {
            double hover = 1.0 / Math.max(1.2, cfg.fpv.thrustToWeight);
            load = 0.25 + motor / hover * 0.75;
        } else {
            load = 1 + 0.3 * Math.min(1, vel.length() / Math.max(1, cfg.speedFor(mode)));
        }
        if (onGround && motor < 0.2) load = 0.1;
        battery = Math.max(0, battery - dt / (minutes * 60) * load);
    }

    public void crash(CrashReason reason) {
        if (crashed) return;
        crashed = true;
        crashReason = reason;
        rates.add((random.nextDouble() - 0.5) * 14, (random.nextDouble() - 0.5) * 8, (random.nextDouble() - 0.5) * 14);
    }

    private void integrateRates(double dt) {
        double angle = rates.length() * dt;
        if (angle < 1e-9) return;
        Quaterniond delta = new Quaterniond().fromAxisAngleRad(rates.x / rates.length(), rates.y / rates.length(), rates.z / rates.length(), angle);
        att.mul(delta).normalize();
    }

    // ---------------------------------------------------------------- outputs

    /** Interpolated camera position and orientation (camera space: -Z forward). */
    public void cameraPose(double alpha, FlightMode mode, DroneConfig cfg, Vector3d outPos, Quaterniond outRot) {
        prevPos.lerp(pos, alpha, outPos);
        if (mode.isFpv() || crashed) {
            prevAtt.slerp(att, alpha, outRot);
            if (mode.isFpv()) {
                outRot.rotateX(Math.toRadians(cfg.camera.fpvUptilt));
                outPos.add(outRot.transform(tmp.set(0, 0.04, -0.1)));
            } else {
                outRot.rotateX(Math.toRadians(gimbal));
            }
        } else {
            double h = prevHeading + (heading - prevHeading) * alpha;
            double g = prevGimbal + (gimbal - prevGimbal) * alpha;
            outRot.identity().rotateY(h).rotateX(Math.toRadians(g));
            outPos.add(new Quaterniond().rotateY(h).transform(tmp.set(0, -0.06, -0.09)));
        }
    }

    public double altitude() {
        return pos.y - home.y;
    }

    public double horizontalDistanceHome() {
        return Math.sqrt(sq(pos.x - home.x) + sq(pos.z - home.z));
    }

    public double horizontalSpeed() {
        return Math.sqrt(vel.x * vel.x + vel.z * vel.z);
    }

    /** Compass heading in degrees, 0 = north, 90 = east. */
    public double compassDegrees() {
        double deg = Math.toDegrees(-heading) % 360;
        return deg < 0 ? deg + 360 : deg;
    }

    /** Pitch of the airframe in degrees, positive = nose up. */
    public double pitchDegrees() {
        Vector3d fwd = att.transform(new Vector3d(0, 0, -1));
        return Math.toDegrees(Math.asin(clamp(fwd.y, -1, 1)));
    }

    /** Roll of the airframe in degrees, positive = right wing down. */
    public double rollDegrees() {
        Vector3d right = att.transform(new Vector3d(1, 0, 0));
        Vector3d up = att.transform(new Vector3d(0, 1, 0));
        return Math.toDegrees(Math.atan2(-right.y, up.y));
    }

    /** Pack voltage of a simulated 4S lipo, with sag under throttle. */
    public double packVoltage() {
        double cell = 3.3 + 0.9 * battery - (battery < 0.15 ? (0.15 - battery) * 2 : 0);
        return Math.max(12.0, cell * 4 - motor * 1.1);
    }

    public static double headingOf(Quaterniond q) {
        Vector3d fwd = q.transform(new Vector3d(0, 0, -1));
        if (fwd.x * fwd.x + fwd.z * fwd.z < 1e-6) {
            Vector3d up = q.transform(new Vector3d(0, 1, 0));
            // Pointing straight up or down: take heading from where the top of the frame faces.
            return Math.atan2(fwd.y > 0 ? up.x : -up.x, fwd.y > 0 ? up.z : -up.z);
        }
        return Math.atan2(-fwd.x, -fwd.z);
    }

    private static double upY(Quaterniond q) {
        return q.transform(new Vector3d(0, 1, 0)).y;
    }

    /** Betaflight "Actual" rates. Returns degrees per second. */
    public static double actualRate(double stick, double center, double max, double expo) {
        double x = clamp(stick, -1, 1);
        double ax = Math.abs(x);
        double expof = ax * (Math.pow(x, 5) * expo + x * (1 - expo));
        return x * center + Math.max(0, max - center) * expof;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
