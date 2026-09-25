package com.belzebool.freefpv.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

/** Client-side particles and sounds around drones: prop wash, water spray, impacts and crash smoke. */
final class DroneEffects {
    /** Prop wash reaches this far below the props. */
    private static final double WASH_HEIGHT = 2.6;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private DroneEffects() {
    }

    /** Surface under a point within {@link #WASH_HEIGHT}: block or fluid top, or null. */
    private record Surface(double y, BlockState block, boolean water) {
    }

    private static Surface surfaceBelow(ClientLevel level, double x, double y, double z) {
        int top = (int) Math.floor(y);
        for (int by = top; by >= Math.floor(y - WASH_HEIGHT) - 1; by--) {
            CURSOR.set(x, by, z);
            FluidState fluid = level.getFluidState(CURSOR);
            if (!fluid.isEmpty()) {
                double h = by + fluid.getHeight(level, CURSOR);
                if (h <= y) return new Surface(h, null, true);
            }
            BlockState state = level.getBlockState(CURSOR);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, CURSOR);
            if (shape.isEmpty()) continue;
            double h = by + shape.max(Direction.Axis.Y);
            if (h <= y + 0.01) return new Surface(h, state, false);
        }
        return null;
    }

    /** Dust or spray kicked up under the props when flying low. Call once per client tick. */
    static void propWash(ClientLevel level, double x, double y, double z, double motor, boolean fpv) {
        if (motor < 0.08) return;
        Surface s = surfaceBelow(level, x, y, z);
        if (s == null) return;
        double height = y - s.y();
        if (height > WASH_HEIGHT) return;
        double strength = motor * (1 - height / WASH_HEIGHT) * (fpv ? 1.4 : 1.0);
        int count = (int) (strength * 5 + RANDOM.nextDouble());
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 0.15 + RANDOM.nextDouble() * 0.5;
            double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
            double out = 0.08 + strength * 0.25 * RANDOM.nextDouble();
            if (s.water()) {
                level.addParticle(ParticleTypes.SPLASH, px, s.y() + 0.05, pz, Math.cos(a) * out, 0.12 + strength * 0.2, Math.sin(a) * out);
            } else if (RANDOM.nextFloat() < 0.8f) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s.block()), px, s.y() + 0.05, pz,
                    Math.cos(a) * out, 0.04 + 0.08 * RANDOM.nextDouble(), Math.sin(a) * out);
            } else {
                level.addParticle(ParticleTypes.POOF, px, s.y() + 0.1, pz, Math.cos(a) * out * 0.5, 0.01, Math.sin(a) * out * 0.5);
            }
        }
    }

    /** Debris and a knock from the block that was hit. {@code dir} points from the drone into the surface. */
    static void impact(ClientLevel level, Vector3d pos, Vector3d dir, double speed, boolean crash, float volume) {
        double hx = pos.x + dir.x * 0.25, hy = pos.y + dir.y * 0.25, hz = pos.z + dir.z * 0.25;
        CURSOR.set(hx, hy, hz);
        BlockState state = level.getBlockState(CURSOR);
        if (state.isAir()) {
            CURSOR.set(pos.x, pos.y - 0.2, pos.z);
            state = level.getBlockState(CURSOR);
        }
        double k = Math.min(1, speed / 12);
        if (!state.isAir()) {
            int count = (int) (4 + k * 18);
            for (int i = 0; i < count; i++) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z,
                    (RANDOM.nextDouble() - 0.5) * 0.3 - dir.x * 0.15, RANDOM.nextDouble() * 0.2 - dir.y * 0.1,
                    (RANDOM.nextDouble() - 0.5) * 0.3 - dir.z * 0.15);
            }
            level.playLocalSound(pos.x, pos.y, pos.z, state.getSoundType().getHitSound(), SoundSource.NEUTRAL,
                volume * (float) (0.4 + k), 0.9f + RANDOM.nextFloat() * 0.3f, false);
        }
        if (crash) {
            for (int i = 0; i < 6; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z,
                    (RANDOM.nextDouble() - 0.5) * 0.4, RANDOM.nextDouble() * 0.3, (RANDOM.nextDouble() - 0.5) * 0.4);
            }
            level.playLocalSound(pos.x, pos.y, pos.z, net.minecraft.sounds.SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL,
                volume * 0.8f, 1.3f + RANDOM.nextFloat() * 0.2f, false);
        }
    }

    /**
     * The drone breaking up: plastic and carbon shards in its own colours, a puff of dust and, for FPV quads, a
     * burst of smoke from the lipo. Seen by the pilot and, with telemetry, by everyone around.
     */
    static void crashBurst(ClientLevel level, double x, double y, double z, boolean fpv) {
        BlockState body = (fpv ? Blocks.BLACK_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
        BlockState trim = (fpv ? Blocks.ORANGE_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState();
        for (int i = 0; i < 18; i++) {
            BlockState piece = i % 4 == 0 ? trim : body;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, piece), x, y + 0.1, z,
                (RANDOM.nextDouble() - 0.5) * 0.5, 0.15 + RANDOM.nextDouble() * 0.3, (RANDOM.nextDouble() - 0.5) * 0.5);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(ParticleTypes.POOF, x + (RANDOM.nextDouble() - 0.5) * 0.4, y + 0.1,
                z + (RANDOM.nextDouble() - 0.5) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.05, 0.03, (RANDOM.nextDouble() - 0.5) * 0.05);
        }
        if (fpv) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, (RANDOM.nextDouble() - 0.5) * 0.04, 0.05, (RANDOM.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    /** A ring of dust when the drone lifts off or sets down. */
    static void groundPuff(ClientLevel level, double x, double y, double z) {
        Surface s = surfaceBelow(level, x, y, z);
        if (s == null || y - s.y() > 0.6) return;
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + RANDOM.nextDouble() * 0.3;
            double vx = Math.cos(a) * 0.12, vz = Math.sin(a) * 0.12;
            if (s.water()) {
                level.addParticle(ParticleTypes.SPLASH, x + vx * 3, s.y() + 0.05, z + vz * 3, vx, 0.1, vz);
            } else {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s.block()), x + vx * 2, s.y() + 0.05, z + vz * 2, vx, 0.03, vz);
                if (i % 3 == 0) level.addParticle(ParticleTypes.POOF, x + vx * 2, s.y() + 0.1, z + vz * 2, vx * 0.3, 0.01, vz * 0.3);
            }
        }
    }

    /** Rain hitting the spinning props bounces off as spray. Call once per client tick. */
    static void rainOnProps(ClientLevel level, double x, double y, double z, double motor) {
        if (motor < 0.15) return;
        CURSOR.set(x, y + 1, z);
        if (!level.isRainingAt(CURSOR)) return;
        for (int i = 0; i < 2; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.SPLASH, x + Math.cos(a) * 0.25, y + 0.1, z + Math.sin(a) * 0.25,
                Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
        }
    }

    /** A thin trail of smoke from a crashed quad. Call once per client tick. */
    static void crashSmoke(ClientLevel level, double x, double y, double z) {
        if (RANDOM.nextFloat() < 0.6f) {
            level.addParticle(ParticleTypes.SMOKE, x + (RANDOM.nextDouble() - 0.5) * 0.2, y + 0.1,
                z + (RANDOM.nextDouble() - 0.5) * 0.2, 0, 0.03, 0);
        }
    }

    static void splash(ClientLevel level, double x, double y, double z) {
        for (int i = 0; i < 16; i++) {
            level.addParticle(ParticleTypes.SPLASH, x + (RANDOM.nextDouble() - 0.5) * 0.6, y + 0.1,
                z + (RANDOM.nextDouble() - 0.5) * 0.6, 0, 0.2, 0);
        }
        level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6f, 1.4f, false);
    }
}
