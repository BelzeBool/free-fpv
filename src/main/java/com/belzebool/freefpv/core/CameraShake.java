package com.belzebool.freefpv.core;

import org.joml.Quaterniond;

import java.util.Random;

/**
 * Camera shake as smooth noise rather than per-frame jitter: a constant buzz from motors and wind plus "trauma" from
 * impacts that decays quickly. Rotation amplitudes are in degrees.
 */
public final class CameraShake {
    private static final int OCTAVES = 3;

    private final double[] phase = new double[OCTAVES * 3];
    private double time;
    private double trauma;
    private double buzz;

    public CameraShake() {
        Random random = new Random();
        for (int i = 0; i < phase.length; i++) phase[i] = random.nextDouble() * Math.PI * 2;
    }

    public void reset() {
        trauma = 0;
        buzz = 0;
    }

    /** Adds an impulse, 0..1; stronger impacts shake harder and longer. */
    public void addTrauma(double amount) {
        trauma = Math.min(1, trauma + Math.max(0, amount));
    }

    /**
     * @param buzzDegrees steady vibration amplitude this frame (motors, prop wash, wind)
     */
    public void update(double dt, double buzzDegrees) {
        time += dt;
        buzz += (buzzDegrees - buzz) * (1 - Math.exp(-dt * 6));
        trauma = Math.max(0, trauma - dt * 1.6);
    }

    /** Rotates the camera by the current shake, in its own frame. {@code scale} is the user setting. */
    public void apply(Quaterniond rotation, double scale) {
        if (scale <= 0) return;
        double hit = trauma * trauma * 4.0;
        double pitch = scale * (buzz * noise(0, 23) + hit * noise(0, 9));
        double yaw = scale * (buzz * 0.6 * noise(1, 19) + hit * 0.8 * noise(1, 8));
        double roll = scale * (buzz * 0.8 * noise(2, 17) + hit * 1.2 * noise(2, 11));
        rotation.rotateX(Math.toRadians(pitch)).rotateY(Math.toRadians(yaw)).rotateZ(Math.toRadians(roll));
    }

    /** Sum of sines at non-harmonic frequencies, roughly -1..1. */
    private double noise(int axis, double baseHz) {
        double sum = 0, amp = 0.6;
        for (int o = 0; o < OCTAVES; o++) {
            double hz = baseHz * (1 + o * 0.73);
            sum += Math.sin(time * hz * Math.PI * 2 + phase[axis * OCTAVES + o]) * amp;
            amp *= 0.5;
        }
        return sum;
    }

    public double trauma() {
        return trauma;
    }
}
