package com.belzebool.freefpv.client;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.core.DroneConfig;
import com.belzebool.freefpv.mixin.ItemDisplayAccessor;
import com.belzebool.freefpv.net.DroneInfoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Other pilots' drones, found as item displays carrying a Free FPV model: propeller sound, prop wash and who is
 * piloting. Works from the display alone on any server; {@link DroneInfoPayload} from a Free FPV server adds the
 * real motor load, crash state and the pilot's entity for the "holding the remote" pose.
 */
final class RemoteDrones {
    private static final long INFO_TIMEOUT_NANOS = 1_000_000_000L;
    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    private static final Map<Integer, Info> INFO = new HashMap<>();
    private static int scanCooldown;

    private static final class Tracked {
        Display.ItemDisplay entity;
        double lastX, lastY, lastZ;
        double speed;
        boolean seen;
        boolean fpv;
        float volumeScale;
    }

    private record Info(int pilotId, boolean fpv, boolean crashed, float motor, long time) {
    }

    private RemoteDrones() {
    }

    static void onInfo(DroneInfoPayload payload) {
        long now = System.nanoTime();
        for (DroneInfoPayload.Entry e : payload.drones()) {
            INFO.put(e.droneId(), new Info(e.pilotId(), e.fpv(), e.crashed(), e.motor(), now));
        }
    }

    private static Info info(int droneId) {
        Info info = INFO.get(droneId);
        return info != null && System.nanoTime() - info.time() < INFO_TIMEOUT_NANOS ? info : null;
    }

    /** 0 = not piloting, 1 = camera drone pilot, 2 = FPV pilot (goggles on). */
    static int pilotMode(int entityId) {
        long now = System.nanoTime();
        for (Info info : INFO.values()) {
            if (info.pilotId() == entityId && now - info.time() < INFO_TIMEOUT_NANOS) return info.fpv() ? 2 : 1;
        }
        return 0;
    }

    static void tick(Minecraft mc, int ownId, DroneConfig config) {
        if (mc.level == null) {
            TRACKED.clear();
            INFO.clear();
            return;
        }
        double volume = config.general.soundVolume;
        for (Tracked t : TRACKED.values()) {
            double dx = t.entity.getX() - t.lastX, dy = t.entity.getY() - t.lastY, dz = t.entity.getZ() - t.lastZ;
            t.speed += (Math.sqrt(dx * dx + dy * dy + dz * dz) * 20 - t.speed) * 0.3;
            t.lastX = t.entity.getX();
            t.lastY = t.entity.getY();
            t.lastZ = t.entity.getZ();
            t.volumeScale = (float) volume;
            if (config.effects.particles && !t.entity.isRemoved()) {
                Info info = info(t.entity.getId());
                double motor = info != null ? info.motor() : estimatedMotor(t);
                if (info == null || !info.crashed()) {
                    DroneEffects.propWash(mc.level, t.entity.getX(), t.entity.getY(), t.entity.getZ(), motor, t.fpv);
                } else {
                    DroneEffects.crashSmoke(mc.level, t.entity.getX(), t.entity.getY(), t.entity.getZ());
                }
            }
        }
        if (--scanCooldown > 0) return;
        scanCooldown = 10;
        long now = System.nanoTime();
        INFO.values().removeIf(info -> now - info.time() > INFO_TIMEOUT_NANOS * 5);

        TRACKED.values().forEach(t -> t.seen = false);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Display.ItemDisplay display) || entity.getId() == ownId) continue;
            Identifier model = ((ItemDisplayAccessor) display).freefpv$getItemStack().get(DataComponents.ITEM_MODEL);
            if (model == null || !model.getNamespace().equals(FreeFpv.MOD_ID)) continue;
            Tracked t = TRACKED.get(entity.getId());
            if (t == null) {
                t = new Tracked();
                t.entity = display;
                t.lastX = entity.getX();
                t.lastY = entity.getY();
                t.lastZ = entity.getZ();
                t.fpv = model.getPath().contains("fpv");
                t.volumeScale = (float) volume;
                TRACKED.put(entity.getId(), t);
                Tracked tracked = t;
                mc.getSoundManager().play(new DroneSound(() -> sample(tracked)));
            }
            t.seen = true;
        }
        TRACKED.values().removeIf(t -> !t.seen || t.entity.isRemoved());
    }

    private static double estimatedMotor(Tracked t) {
        double load = Math.min(1, t.speed / (t.fpv ? 25 : 12));
        return t.fpv ? 0.25 + load * 0.6 : 0.45 + load * 0.15;
    }

    private static DroneSound.Sample sample(Tracked t) {
        if (t.entity.isRemoved() || !TRACKED.containsKey(t.entity.getId())) return null;
        Info info = info(t.entity.getId());
        float pitch, volume;
        if (info != null) {
            // Same curve as the pilot hears, so everyone hears the quad spool up on a punch-out.
            double motor = info.crashed() ? 0 : info.motor();
            pitch = (float) (t.fpv ? 0.8 + motor * 1.1 : 1.15 + motor * 0.5);
            volume = t.volumeScale * (float) (0.35 + motor) * (info.crashed() ? 0.1f : 1f);
        } else {
            double load = Math.min(1, t.speed / (t.fpv ? 25 : 12));
            pitch = (float) (t.fpv ? 1.0 + load * 0.8 : 1.2 + load * 0.4);
            volume = t.volumeScale * (float) (0.5 + load * 0.5);
        }
        return new DroneSound.Sample(t.entity.getX(), t.entity.getY(), t.entity.getZ(), volume, pitch);
    }
}
