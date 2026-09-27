package com.belzebool.freefpv.client.race;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.mixin.DisplayAccessor;
import com.belzebool.freefpv.mixin.ItemDisplayAccessor;
import com.belzebool.freefpv.net.GhostPayload;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else
//import net.minecraft.world.entity.EntityType;

/**
 * The pilot's best lap replayed as a translucent drone. It is a client-only item display (negative entity id, so it
 * never collides with ids the server hands out) moved every tick along the recorded path.
 */
final class GhostDrone {
    private static final int ID = -774_411;

    private float[] samples;
    private boolean fpv;
    private long lapStartMs;
    private Display.ItemDisplay entity;
    private ClientLevel level;

    void start(float[] samples, boolean fpv, long lapStartMs) {
        this.samples = samples;
        this.fpv = fpv;
        this.lapStartMs = lapStartMs;
    }

    void stop() {
        samples = null;
        remove();
    }

    private void remove() {
        if (entity != null && level != null && level.getEntity(ID) == entity) level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        entity = null;
        level = null;
    }

    void tick(Minecraft mc, long clockMs) {
        if (samples == null || samples.length < GhostPayload.STRIDE * 2 || mc.level == null) {
            remove();
            return;
        }
        float t = clockMs - lapStartMs;
        int n = samples.length / GhostPayload.STRIDE;
        float end = samples[(n - 1) * GhostPayload.STRIDE];
        if (t > end + 1000) {
            remove();
            return;
        }
        if (entity == null || level != mc.level || entity.isRemoved()) spawn(mc.level);

        int i = 0;
        while (i < n - 2 && samples[(i + 1) * GhostPayload.STRIDE] < t) i++;
        int a = i * GhostPayload.STRIDE, b = (i + 1) * GhostPayload.STRIDE;
        float span = Math.max(1, samples[b] - samples[a]);
        float f = Math.max(0, Math.min(1, (t - samples[a]) / span));
        double x = lerp(samples[a + 1], samples[b + 1], f);
        double y = lerp(samples[a + 2], samples[b + 2], f);
        double z = lerp(samples[a + 3], samples[b + 3], f);
        Quaternionf qa = new Quaternionf(samples[a + 4], samples[a + 5], samples[a + 6], samples[a + 7]);
        Quaternionf qb = new Quaternionf(samples[b + 4], samples[b + 5], samples[b + 6], samples[b + 7]);
        Quaternionf q = qa.slerp(qb, f).normalize();
        entity.setPos(x, y, z);
        // Flying the same line as the ghost would put it right on the lens: shrink it away when it gets that close.
        var cam = mc.gameRenderer./*? if >=26.2 {*/mainCamera/*?} else {*//*getMainCamera*//*?}*/().position();
        double near = Math.sqrt((cam.x - x) * (cam.x - x) + (cam.y - y) * (cam.y - y) + (cam.z - z) * (cam.z - z));
        float fade = (float) Math.max(0, Math.min(1, (near - 1.2) / 1.3));
        ((DisplayAccessor) entity).freefpv$setTransformation(new Transformation(null, q, new Vector3f((fpv ? 0.5f : 0.55f) * fade), null));
    }

    private void spawn(ClientLevel target) {
        remove();
        Display.ItemDisplay display = new Display.ItemDisplay(/*? if >=26.2 {*/EntityTypes/*?} else {*//*EntityType*//*?}*/.ITEM_DISPLAY, target);
        display.setId(ID);
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, FreeFpv.id(fpv ? "drone_fpv_ghost" : "drone_camera_ghost"));
        ((ItemDisplayAccessor) display).freefpv$setItemStack(stack);
        ((DisplayAccessor) display).freefpv$setPosRotInterpolationDuration(1);
        ((DisplayAccessor) display).freefpv$setTransformationInterpolationDuration(1);
        display.setPos(samples[1], samples[2], samples[3]);
        target.addEntity(display);
        entity = display;
        level = target;
    }

    private static double lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }
}
