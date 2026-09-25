package com.belzebool.freefpv.server;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.net.DroneInfoPayload;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.net.OwnDronePayload;
import com.belzebool.freefpv.net.ServerConfigPayload;
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
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows every pilot's drone to everyone else as a vanilla item display entity. Vanilla tracking and interpolation do
 * the rest, so players without the mod see it too (as long as they have the model from the mod or a resource pack).
 * Players with the mod also get {@link DroneInfoPayload} telemetry for sound, prop wash and pilot poses.
 */
public final class DroneServer {
    /** Pilots can't park a drone further than this from themselves, whatever their client claims. */
    private static final double HARD_RANGE = 600;
    private static final int TIMEOUT_TICKS = 40;
    private static final int INFO_INTERVAL = 2;
    private static final double INFO_RANGE = 128;
    private static final float CAMERA_SCALE = 0.55f;
    private static final float FPV_SCALE = 0.5f;

    private static final Map<UUID, Drone> DRONES = new HashMap<>();
    private static Entity spawning;
    private static ServerConfig config;

    private static final class Drone {
        Display.ItemDisplay entity;
        int pilotId;
        boolean fpv;
        int flags;
        float motor;
        int lastUpdate;
    }

    private DroneServer() {
    }

    public static ServerConfig config() {
        if (config == null) config = ServerConfig.load(Platform.INSTANCE.configDir().resolve("freefpv-server.json"));
        return config;
    }

    public static void onServerStarting() {
        config = null;
        config();
    }

    /** Tells a joining player with the mod what this server allows. */
    public static void onJoin(ServerPlayer player) {
        ServerConfig c = config();
        Platform.INSTANCE.sendToPlayer(player, new ServerConfigPayload(ServerConfigPayload.PROTOCOL, (float) c.maxRange, List.copyOf(c.tools)));
    }

    public static void handleState(ServerPlayer player, DroneStatePayload msg) {
        if (!msg.active()) {
            remove(player.getUUID());
            return;
        }
        if (!Double.isFinite(msg.x()) || !Double.isFinite(msg.y()) || !Double.isFinite(msg.z())) return;
        ServerConfig c = config();
        if (!c.allows(msg.fpv() ? ServerConfig.FPV_RADIO : ServerConfig.CAMERA_REMOTE)) {
            remove(player.getUUID());
            return;
        }
        double range = c.maxRange > 0 ? Math.min(HARD_RANGE, c.maxRange * 1.2 + 16) : HARD_RANGE;
        if (player.distanceToSqr(msg.x(), msg.y(), msg.z()) > range * range) return;

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
        drone.pilotId = player.getId();
        drone.flags = (msg.fpv() ? DroneInfoPayload.FPV : 0) | (msg.flags() & (DroneInfoPayload.CRASHED | DroneInfoPayload.AUTOPILOT));
        drone.motor = Float.isFinite(msg.motor()) ? Math.max(0, Math.min(1, msg.motor())) : 0;

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
        Display.ItemDisplay entity = new Display.ItemDisplay(/*? if >=26.2 {*/EntityTypes/*?} else {*//*EntityType*//*?}*/.ITEM_DISPLAY, level);
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
        if (now % INFO_INTERVAL == 0 && !DRONES.isEmpty() && config().shareTelemetry) broadcastInfo(server);
    }

    /** Sends each modded player the drones near them, or whose pilot is near them. */
    private static void broadcastInfo(MinecraftServer server) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<DroneInfoPayload.Entry> near = new ArrayList<>();
            for (Map.Entry<UUID, Drone> entry : DRONES.entrySet()) {
                Drone d = entry.getValue();
                if (d.entity.level() != viewer.level()) continue;
                Entity pilot = viewer.level().getEntity(d.pilotId);
                boolean close = viewer.distanceToSqr(d.entity) < INFO_RANGE * INFO_RANGE
                    || pilot != null && viewer.distanceToSqr(pilot) < INFO_RANGE * INFO_RANGE;
                if (close) near.add(new DroneInfoPayload.Entry(d.entity.getId(), d.pilotId, d.flags, d.motor));
            }
            if (!near.isEmpty()) Platform.INSTANCE.sendToPlayer(viewer, new DroneInfoPayload(near));
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
        if (entity == spawning || !(entity instanceof Display.ItemDisplay) || !entity./*? if >=26.1 {*/entityTags/*?} else {*//*getTags*//*?}*/().contains(FreeFpv.DRONE_TAG)) return false;
        for (Drone drone : DRONES.values()) {
            if (drone.entity == entity) return false;
        }
        return true;
    }
}
