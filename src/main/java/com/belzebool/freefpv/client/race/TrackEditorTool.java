package com.belzebool.freefpv.client.race;

import com.belzebool.freefpv.client.Compat;
import com.belzebool.freefpv.client.tools.Multitool;
import com.belzebool.freefpv.client.tools.ToolSlot;
import com.belzebool.freefpv.core.race.Gate;
import com.belzebool.freefpv.core.race.GateType;
import com.belzebool.freefpv.core.race.Track;
import com.belzebool.freefpv.net.TrackEditPayload;
import com.belzebool.freefpv.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Builds race tracks in creative mode. Right click places a gate where you look, facing the way you look (that is the
 * direction pilots must fly through it); left click on a gate opens its settings, left click elsewhere changes the
 * kind of gate to place. The first gate of a new track is the start/finish.
 */
public final class TrackEditorTool implements Multitool {
    public static final String ID = "freefpv:track_editor";
    public static final TrackEditorTool INSTANCE = new TrackEditorTool();
    private static final double REACH = 96;

    /** Track new gates go to; empty = the next gate starts a new track. */
    String selectedTrack = "";
    private GateType placeType = GateType.STANDARD;
    float placeScale = 1;

    private TrackEditorTool() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String icon() {
        return "freefpv:gate_standard";
    }

    @Override
    public Component name() {
        return Component.translatable("tool.freefpv.track_editor");
    }

    @Override
    public Component description() {
        return Component.translatable("tool.freefpv.track_editor.desc");
    }

    @Override
    public boolean needsServer() {
        return true;
    }

    @Override
    public String status() {
        Track t = selected();
        String track = t == null ? Component.translatable("race.freefpv.new_track").getString() : t.name;
        return track + " · " + Component.translatable("race.freefpv.gate." + (t == null ? "start" : placeType.id)).getString();
    }

    Track selected() {
        for (Track t : ClientRace.tracks()) if (t.id.equals(selectedTrack)) return t;
        return null;
    }

    @Override
    public void use(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!player.isCreative()) {
            Compat.actionBar(player, Component.translatable("race.freefpv.creative_only"));
            return;
        }
        HitResult hit = player.pick(REACH, 1, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return;
        Vec3 at = block.getLocation();
        Direction face = block.getDirection();
        double x = at.x, y = at.y, z = at.z;
        if (face != Direction.UP) {
            x += face.getStepX() * 0.6;
            z += face.getStepZ() * 0.6;
            if (face == Direction.DOWN) y -= 3;
        }
        GateType type = selected() == null ? GateType.START : placeType;
        if (type == GateType.DIVE) y += 2.5;
        float yaw = Math.round(player.getYRot() / 15f) * 15f;
        Platform.INSTANCE.sendToServer(TrackEditPayload.place(selectedTrack, type.id, x, y, z, yaw, placeScale));
        TrackEditorScreen.pendingSelectNewest = selected() == null;
    }

    @Override
    public void secondary(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        Object[] picked = ClientRace.pick(new Vector3d(eye.x, eye.y, eye.z), new Vector3d(look.x, look.y, look.z), REACH);
        if (picked != null && player.isCreative()) {
            Track track = (Track) picked[0];
            selectedTrack = track.id;
            Compat.setScreen(mc, new TrackEditorScreen(track.id, ((Gate) picked[1]).id));
            return;
        }
        placeType = placeType.nextPlaceable();
        ToolSlot.INSTANCE.showName();
    }

    /** Called every tick while the tool is in the slot: outlines of the gates and a preview of the next one. */
    public void tick(Minecraft mc) {
        if (Compat.screen(mc) != null) return;
        ClientRace.editorTick(mc);
        if (TrackEditorScreen.pendingSelectNewest) {
            // The server created a track for the first gate: follow it so the next gates join it.
            Track newest = null;
            for (Track t : ClientRace.tracks()) newest = t;
            if (newest != null && !newest.id.equals(selectedTrack) && newest.gates.size() == 1) {
                selectedTrack = newest.id;
                TrackEditorScreen.pendingSelectNewest = false;
            }
        }
    }

    public boolean isHeld() {
        return ToolSlot.INSTANCE.isActive() && ToolSlot.INSTANCE.selected() == this;
    }
}
