package com.belzebool.freefpv.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.Supplier;

/** Propeller whine: the bee loop pitched up, following a drone and its motor load. */
public final class DroneSound extends AbstractTickableSoundInstance {
    public record Sample(double x, double y, double z, float volume, float pitch) {
    }

    private final Supplier<Sample> source;

    public DroneSound(Supplier<Sample> source) {
        super(SoundEvents.BEE_LOOP, SoundSource.NEUTRAL, RandomSource.create());
        this.source = source;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        Sample first = source.get();
        if (first != null) {
            this.x = first.x();
            this.y = first.y();
            this.z = first.z();
        }
    }

    @Override
    public void tick() {
        Sample sample = source.get();
        if (sample == null) {
            stop();
            return;
        }
        x = sample.x();
        y = sample.y();
        z = sample.z();
        volume = Math.max(0.001f, sample.volume());
        pitch = Math.max(0.5f, Math.min(2.0f, sample.pitch()));
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
