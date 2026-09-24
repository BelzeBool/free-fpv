package com.belzebool.freefpv.client;

import com.belzebool.freefpv.core.DroneWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;

/** Block collision and liquids for the flight model, read from the client world. */
final class McDroneWorld implements DroneWorld {
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    @Override
    public void collide(Vector3d c, Vector3d h, Vector3d motion, Vector3d result) {
        ClientLevel level = level();
        if (level == null) {
            result.set(motion);
            return;
        }
        AABB box = new AABB(c.x - h.x, c.y - h.y, c.z - h.z, c.x + h.x, c.y + h.y, c.z + h.z);
        Vec3 allowed = Entity.collideBoundingBox((Entity) null, new Vec3(motion.x, motion.y, motion.z), box, level, List.of());
        result.set(allowed.x, allowed.y, allowed.z);
    }

    /**
     * Keeps the camera out of blocks: sweeps a small box from the drone centre to where the lens wants to be and
     * stops it at the first surface. With the radius larger than the near-plane corners, walls can't be seen through
     * even when the drone is pressed against them or tumbling after a crash.
     */
    void clampCamera(Vector3d center, Vector3d camera, double radius) {
        ClientLevel level = level();
        if (level == null) return;
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
        Vec3 wanted = new Vec3(camera.x - center.x, camera.y - center.y, camera.z - center.z);
        Vec3 allowed = Entity.collideBoundingBox((Entity) null, wanted, box, level, List.of());
        camera.set(center.x + allowed.x, center.y + allowed.y, center.z + allowed.z);
    }

    @Override
    public boolean isInLiquid(double x, double y, double z) {
        ClientLevel level = level();
        if (level == null) return false;
        cursor.set(x, y, z);
        FluidState fluid = level.getFluidState(cursor);
        return !fluid.isEmpty() && y < cursor.getY() + fluid.getHeight(level, cursor);
    }

    @Override
    public boolean isLoaded(double x, double y, double z) {
        ClientLevel level = level();
        return level != null && level.hasChunkAt(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
    }
}
