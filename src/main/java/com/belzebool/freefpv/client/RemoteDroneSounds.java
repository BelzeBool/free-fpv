package com.belzebool.freefpv.client;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.mixin.ItemDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** Propeller sound for other pilots' drones, found as item displays carrying a Free FPV model. */
final class RemoteDroneSounds {
    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    private static int scanCooldown;

    private static final class Tracked {
        Display.ItemDisplay entity;
        double lastX, lastY, lastZ;
        double speed;
        boolean seen;
        boolean fpv;
        float volumeScale;
    }

    private RemoteDroneSounds() {
    }

    static void tick(Minecraft mc, int ownId, double volume) {
        if (mc.level == null) {
            TRACKED.clear();
            return;
        }
        for (Tracked t : TRACKED.values()) {
            double dx = t.entity.getX() - t.lastX, dy = t.entity.getY() - t.lastY, dz = t.entity.getZ() - t.lastZ;
            t.speed += (Math.sqrt(dx * dx + dy * dy + dz * dz) * 20 - t.speed) * 0.3;
            t.lastX = t.entity.getX();
            t.lastY = t.entity.getY();
            t.lastZ = t.entity.getZ();
            t.volumeScale = (float) volume;
        }
        if (--scanCooldown > 0) return;
        scanCooldown = 10;

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

    private static DroneSound.Sample sample(Tracked t) {
        if (t.entity.isRemoved() || !TRACKED.containsKey(t.entity.getId())) return null;
        double load = Math.min(1, t.speed / (t.fpv ? 25 : 12));
        float pitch = (float) (t.fpv ? 1.0 + load * 0.8 : 1.2 + load * 0.4);
        float volume = t.volumeScale * (float) (0.5 + load * 0.5);
        return new DroneSound.Sample(t.entity.getX(), t.entity.getY(), t.entity.getZ(), volume, pitch);
    }
}
