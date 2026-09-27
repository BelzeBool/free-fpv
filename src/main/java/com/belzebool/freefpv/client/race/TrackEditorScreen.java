package com.belzebool.freefpv.client.race;

import com.belzebool.freefpv.core.race.Gate;
import com.belzebool.freefpv.core.race.GateType;
import com.belzebool.freefpv.core.race.Track;
import com.belzebool.freefpv.net.TrackEditPayload;
import com.belzebool.freefpv.net.TrackEditPayload.Action;
import com.belzebool.freefpv.platform.Platform;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Creative-mode settings of a track and the gate that was clicked. Every change is sent to the server at once. */
public final class TrackEditorScreen extends Screen {
    static boolean pendingSelectNewest;

    private final String trackId;
    private final String gateId;
    private EditBox nameBox;
    private boolean confirmDelete;

    public TrackEditorScreen(String trackId, String gateId) {
        super(Component.translatable("race.freefpv.editor"));
        this.trackId = trackId;
        this.gateId = gateId;
    }

    private Track track() {
        for (Track t : ClientRace.tracks()) if (t.id.equals(trackId)) return t;
        return null;
    }

    @Override
    protected void init() {
        Track track = track();
        if (track == null) {
            onClose();
            return;
        }
        Gate gate = track.gate(gateId);
        int index = track.indexOf(gateId);
        int cx = width / 2, y = height / 2 - 100;

        addRenderableWidget(new StringWidget(cx - 150, y, 300, 12, Component.translatable("race.freefpv.editor"), font));
        y += 18;
        nameBox = new EditBox(font, 196, 20, Component.translatable("race.freefpv.track_name"));
        nameBox.setPosition(cx - 150, y);
        nameBox.setMaxLength(32);
        nameBox.setValue(track.name);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("race.freefpv.rename"), b -> {
            sendTrack(Action.UPDATE_TRACK, nameBox.getValue(), 0);
        }).bounds(cx + 50, y, 100, 20).build());
        y += 24;

        addRenderableWidget(new StringWidget(cx - 150, y + 4, 90, 12, Component.translatable("race.freefpv.laps", track.laps), font));
        addRenderableWidget(Button.builder(Component.literal("−"), b -> sendTrack(Action.UPDATE_TRACK, "", Math.max(1, track.laps - 1)))
            .bounds(cx - 55, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> sendTrack(Action.UPDATE_TRACK, "", Math.min(99, track.laps + 1)))
            .bounds(cx - 33, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("race.freefpv.new_track"), b -> {
            TrackEditorTool.INSTANCE.selectedTrack = "";
            onClose();
        }).bounds(cx + 50, y, 100, 20).build());
        y += 32;

        if (gate != null) {
            String kind = Component.translatable("race.freefpv.gate." + gate.type.id).getString();
            addRenderableWidget(new StringWidget(cx - 150, y, 300, 12,
                Component.translatable("race.freefpv.gate_title", index + 1, track.gates.size(), kind), font));
            y += 16;
            boolean start = index == 0;
            Button type = Button.builder(Component.translatable("race.freefpv.gate_type"), b ->
                sendGate(Action.UPDATE_GATE, gate.type.nextPlaceable(), gate.scale, -1)).bounds(cx - 150, y, 96, 20).build();
            type.active = !start;
            addRenderableWidget(type);
            Button earlier = Button.builder(Component.translatable("race.freefpv.earlier"), b ->
                sendGate(Action.UPDATE_GATE, gate.type, gate.scale, index - 1)).bounds(cx - 50, y, 96, 20).build();
            earlier.active = index > 1;
            addRenderableWidget(earlier);
            Button later = Button.builder(Component.translatable("race.freefpv.later"), b ->
                sendGate(Action.UPDATE_GATE, gate.type, gate.scale, index + 1)).bounds(cx + 50, y, 100, 20).build();
            later.active = index > 0 && index < track.gates.size() - 1;
            addRenderableWidget(later);
            y += 24;
            addRenderableWidget(new StringWidget(cx - 150, y + 4, 90, 12,
                Component.translatable("race.freefpv.size", String.format(Locale.ROOT, "%.1f", gate.scale)), font));
            addRenderableWidget(Button.builder(Component.literal("−"), b -> sendGate(Action.UPDATE_GATE, gate.type, gate.scale - 0.25f, -1))
                .bounds(cx - 55, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> sendGate(Action.UPDATE_GATE, gate.type, gate.scale + 0.25f, -1))
                .bounds(cx - 33, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("race.freefpv.remove_gate"), b -> {
                sendGate(Action.REMOVE_GATE, gate.type, gate.scale, -1);
                onClose();
            }).bounds(cx + 50, y, 100, 20).build());
            y += 32;
        }

        addRenderableWidget(Button.builder(Component.translatable("race.freefpv.clear_records"), b -> {
            sendTrack(Action.CLEAR_RECORDS, "", 0);
        }).bounds(cx - 150, y, 146, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(confirmDelete ? "race.freefpv.remove_track_confirm" : "race.freefpv.remove_track"), b -> {
            if (!confirmDelete) {
                confirmDelete = true;
                rebuildWidgets();
                return;
            }
            sendTrack(Action.REMOVE_TRACK, "", 0);
            TrackEditorTool.INSTANCE.selectedTrack = "";
            onClose();
        }).bounds(cx + 4, y, 146, 20).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 50, y, 100, 20).build());
    }

    private void sendTrack(Action action, String name, int laps) {
        Platform.INSTANCE.sendToServer(TrackEditPayload.track(action, trackId, name, laps));
        refreshSoon();
    }

    private void sendGate(Action action, GateType type, float scale, int index) {
        Platform.INSTANCE.sendToServer(TrackEditPayload.gate(action, trackId, gateId, type.id, Math.max(0.5f, Math.min(3f, scale)), index));
        refreshSoon();
    }

    private int refreshIn;

    private void refreshSoon() {
        refreshIn = 4;
    }

    @Override
    public void tick() {
        super.tick();
        // The server answers with the updated track list; redraw the labels once it arrived.
        if (refreshIn > 0 && --refreshIn == 0) {
            String name = nameBox != null ? nameBox.getValue() : null;
            rebuildWidgets();
            if (name != null && nameBox != null) nameBox.setValue(name);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
