package com.belzebool.freefpv.server;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.net.OwnDronePayload;
import com.belzebool.freefpv.mixin.DisplayAccessor;
import com.belzebool.freefpv.mixin.ItemDisplayAccessor;
import com.belzebool.freefpv.platform.Platform;
import com.mojang.math.Transformation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Shows every pilot's drone to everyone else as a vanilla item display entity. Vanilla tracking and interpolation do
 * the rest, so players without the mod see it too (as long as they have the model from the mod or a resource pack).
 */
public final class DroneServer {
    /** Pilots can't park a drone further than this from themselves, whatever their client claims. */
    private static final double HARD_RANGE = 600;
    private static final int TIMEOUT_TICKS = 40;
    private static final float CAMERA_SCALE = 0.55f;
    private static final float FPV_SCALE = 0.5f;

    private static final Map<UUID, Drone> DRONES = new HashMap<>();
    private static Entity spawning;

    private static final class Drone {
        Display.ItemDisplay entity;
        boolean fpv;
        int lastUpdate;
    }

    private DroneServer() {
    }

    public static void handleState(ServerPlayer player, DroneStatePayload msg) {
        if (!msg.active()) {
            remove(player.getUUID());
            return;
        }
        if (!Double.isFinite(msg.x()) || !Double.isFinite(msg.y()) || !Double.isFinite(msg.z())) return;
        if (player.distanceToSqr(msg.x(), msg.y(), msg.z()) > HARD_RANGE * HARD_RANGE) return;

        ServerLevel level = player.level();
        Drone drone = DRONES.get(player.getUUID());
        if (drone != null && (drone.entity.isRemoved() || drone.entity.level() != level || drone.fpv != msg.fpv())) {
            remove(player.getUUID());
            drone = null;
        }
        if (drone == null) {
            drone = new Drone();
            drone.fpv = msg.fpv();
            drone.entity = spawn(level, msg);
            if (drone.entity == null) return;
            DRONES.put(player.getUUID(), drone);
            Platform.INSTANCE.sendToPlayer(player, new OwnDronePayload(drone.entity.getId()));
        }
        drone.lastUpdate = level.getServer().getTickCount();

        Display.ItemDisplay entity = drone.entity;
        entity.setPos(msg.x(), msg.y(), msg.z());
        Quaternionf rotation = new Quaternionf(msg.qx(), msg.qy(), msg.qz(), msg.qw());
        if (!Float.isFinite(rotation.lengthSquared()) || rotation.lengthSquared() < 1e-4f) rotation.identity();
        rotation.normalize();
        float scale = msg.fpv() ? FPV_SCALE : CAMERA_SCALE;
        DisplayAccessor access = (DisplayAccessor) entity;
        access.freefpv$setTransformation(new Transformation(null, rotation, new Vector3f(scale), null));
        access.freefpv$setTransformationInterpolationDelay(0);
    }

    private static Display.ItemDisplay spawn(ServerLevel level, DroneStatePayload msg) {
        Display.ItemDisplay entity = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, FreeFpv.id(msg.fpv() ? "drone_fpv" : "drone_camera"));
        ((ItemDisplayAccessor) entity).freefpv$setItemStack(stack);
        entity.setPos(msg.x(), msg.y(), msg.z());
        DisplayAccessor access = (DisplayAccessor) entity;
        access.freefpv$setPosRotInterpolationDuration(2);
        access.freefpv$setTransformationInterpolationDuration(2);
        access.freefpv$setViewRange(1.5f);
        access.freefpv$setShadowRadius(0.25f);
        access.freefpv$setShadowStrength(0.6f);
        entity.addTag(FreeFpv.DRONE_TAG);
        spawning = entity;
        try {
            return level.addFreshEntity(entity) ? entity : null;
        } finally {
            spawning = null;
        }
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Drone>> it = DRONES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Drone> entry = it.next();
            Drone drone = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || now - drone.lastUpdate > TIMEOUT_TICKS || drone.entity.isRemoved()) {
                drone.entity.discard();
                it.remove();
            }
        }
    }

    public static void remove(UUID pilot) {
        Drone drone = DRONES.remove(pilot);
        if (drone != null) drone.entity.discard();
    }

    public static void clear() {
        DRONES.values().forEach(drone -> drone.entity.discard());
        DRONES.clear();
    }

    /** Drones saved into a chunk by a crash or restart come back as orphans; drop them on load. */
    public static boolean isOrphan(Entity entity) {
        if (entity == spawning || !(entity instanceof Display.ItemDisplay) || !entity.entityTags().contains(FreeFpv.DRONE_TAG)) return false;
        for (Drone drone : DRONES.values()) {
            if (drone.entity == entity) return false;
        }
        return true;
    }
}
