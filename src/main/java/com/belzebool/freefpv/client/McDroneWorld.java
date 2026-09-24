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
