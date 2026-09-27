package com.belzebool.freefpv.server.race;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.core.race.Gate;
import com.belzebool.freefpv.core.race.GateType;
import com.belzebool.freefpv.core.race.RaceSession;
import com.belzebool.freefpv.core.race.RaceTime;
import com.belzebool.freefpv.core.race.Track;
import com.belzebool.freefpv.mixin.DisplayAccessor;
import com.belzebool.freefpv.mixin.ItemDisplayAccessor;
import com.belzebool.freefpv.mixin.TextDisplayAccessor;
import com.belzebool.freefpv.net.DroneInfoPayload;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.net.GhostPayload;
import com.belzebool.freefpv.net.RacePayload;
import com.belzebool.freefpv.net.TrackEditPayload;
import com.belzebool.freefpv.net.TracksPayload;
import com.belzebool.freefpv.platform.Platform;
import com.belzebool.freefpv.server.DroneServer;
import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else
//import net.minecraft.world.entity.EntityType;

/**
 * Race tracks on the server: timing from the pilots' drone telemetry, records, ghosts, and the gates and leaderboards
 * shown in the world as vanilla display entities (so every player sees them, modded or not).
 */
public final class RaceServer {
    public static final String EDITOR_TOOL = "freefpv:track_editor";
    private static final String GATE_TAG = "freefpv_gate:";
    private static final String BOARD_TAG = "freefpv_board:";
    /** Gate models are authored at half size so the big ones fit the item model grid. */
    private static final float MODEL_SCALE = 2f;
    private static final int MAX_GATES = 64;
    private static final int MAX_TRACKS = 64;

    private static MinecraftServer server;
    private static TrackStore store;
    private static final Map<UUID, Pilot> PILOTS = new HashMap<>();
    /** Loaded gate and board entities by gate / track id. Filled when entities spawn or load with their chunk. */
    private static final Map<String, Display> ENTITIES = new HashMap<>();
    private static final Map<UUID, String> LAST_DIMENSION = new HashMap<>();
    private static Entity spawning;

    private static final class Pilot {
        boolean has;
        double x, y, z;
        long clock;
        RaceSession session;
        String reference = "";
        final List<float[]> lap = new ArrayList<>();
    }

    private RaceServer() {
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onServerStarting(MinecraftServer s) {
        server = s;
        store = TrackStore.load(s.getWorldPath(LevelResource.ROOT));
        PILOTS.clear();
        ENTITIES.clear();
        LAST_DIMENSION.clear();
        FreeFpv.LOGGER.info("Loaded {} race track(s)", store.tracks.size());
    }

    public static void onServerStopping() {
        if (store != null) store.save();
        PILOTS.clear();
        ENTITIES.clear();
        store = null;
        server = null;
    }

    public static void onLeave(ServerPlayer player) {
        PILOTS.remove(player.getUUID());
        LAST_DIMENSION.remove(player.getUUID());
    }

    public static void tick(MinecraftServer s) {
        if (store == null || s.getTickCount() % 20 != 0) return;
        // Tracks are per dimension: resend when a player changes dimension.
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            String dim = dimension(player.level());
            if (!dim.equals(LAST_DIMENSION.put(player.getUUID(), dim))) sendTracks(player);
        }
    }

    // ------------------------------------------------------------------ timing

    public static void onDroneState(ServerPlayer player, DroneStatePayload msg) {
        if (store == null) return;
        Pilot pilot = PILOTS.computeIfAbsent(player.getUUID(), u -> new Pilot());
        boolean crashed = (msg.flags() & DroneInfoPayload.CRASHED) != 0;
        long clock = msg.clockMs();
        if (!msg.active() || crashed || clock < pilot.clock || clock - pilot.clock > 2000) {
            // Landed, crashed, relaunched or lost packets: the run is over.
            if (pilot.session != null && pilot.session.running) send(player, RacePayload.reset());
            pilot.session = null;
            pilot.has = msg.active() && !crashed;
            pilot.lap.clear();
            if (pilot.has) remember(pilot, msg);
            return;
        }
        if (!pilot.has) {
            pilot.has = true;
            remember(pilot, msg);
            return;
        }

        if (pilot.session == null) {
            Track best = null;
            double bestT = 2;
            String dim = dimension(player.level());
            for (Track t : store.tracks) {
                if (!t.dimension.equals(dim)) continue;
                double c = RaceSession.startCrossing(t, pilot.x, pilot.y, pilot.z, msg.x(), msg.y(), msg.z());
                if (c >= 0 && c < bestT) {
                    bestT = c;
                    best = t;
                }
            }
            if (best != null) {
                TrackStore.Ghost ghost = store.ghost(best.id, player.getStringUUID());
                pilot.session = new RaceSession(best, ghost != null ? ghost.splits() : null);
                pilot.reference = best.id;
            }
        }

        RaceSession session = pilot.session;
        if (session != null) {
            if (store.track(session.track.id) == null) {
                pilot.session = null;
                send(player, RacePayload.reset());
            } else {
                for (RaceSession.Event e : session.update(pilot.x, pilot.y, pilot.z, pilot.clock, msg.x(), msg.y(), msg.z(), clock)) {
                    onEvent(player, pilot, session, e, msg.fpv());
                }
                if (pilot.session != null && session.running) {
                    pilot.lap.add(new float[]{clock - session.lapStartMs, (float) msg.x(), (float) msg.y(), (float) msg.z(),
                        msg.qx(), msg.qy(), msg.qz(), msg.qw()});
                }
            }
        }
        remember(pilot, msg);
    }

    private static void remember(Pilot pilot, DroneStatePayload msg) {
        pilot.x = msg.x();
        pilot.y = msg.y();
        pilot.z = msg.z();
        pilot.clock = msg.clockMs();
    }

    private static void onEvent(ServerPlayer player, Pilot pilot, RaceSession s, RaceSession.Event e, boolean fpv) {
        Track track = s.track;
        String uuid = player.getStringUUID();
        TrackStore.Ghost pb = store.ghost(track.id, uuid);
        long personalBest = pb != null ? pb.lapMs() : 0;
        int event = switch (e.kind()) {
            case START -> RacePayload.START;
            case GATE -> RacePayload.GATE;
            case LAP -> RacePayload.LAP;
            case FINISH -> RacePayload.FINISH;
        };

        if (e.kind() == RaceSession.Kind.LAP || e.kind() == RaceSession.Kind.FINISH) {
            if (personalBest == 0 || e.lapMs() < personalBest) {
                float[] samples = flatten(pilot.lap, e.lapMs());
                TrackStore.Ghost ghost = new TrackStore.Ghost(e.lapMs(), s.splits(), samples);
                store.saveGhost(track.id, uuid, ghost);
                s.setReference(ghost.splits());
                personalBest = e.lapMs();
            }
            pilot.lap.clear();
        }

        int place = 0;
        if (e.kind() == RaceSession.Kind.FINISH) {
            place = track.submit(new Track.Record(uuid, player.getName().getString(), s.totalMs, s.bestLapMs, System.currentTimeMillis()));
            store.save();
            if (place > 0) {
                refreshBoard(track);
                broadcastTracks(track.dimension);
            }
            player.sendSystemMessage(Component.translatable("race.freefpv.finished", track.name, RaceTime.format(s.totalMs),
                RaceTime.format(s.bestLapMs)).withStyle(ChatFormatting.GOLD));
            pilot.session = null;
        }

        send(player, new RacePayload(track.id, event, s.lap, Math.max(1, track.laps), s.nextGate, track.gates.size(),
            s.raceStartMs, s.lapStartMs, s.lastLapMs, s.bestLapMs, personalBest, track.bestLapOverall(), e.deltaMs(),
            s.totalMs, place));

        if (e.kind() == RaceSession.Kind.START || e.kind() == RaceSession.Kind.LAP) {
            TrackStore.Ghost ghost = store.ghost(track.id, uuid);
            if (ghost != null && ghost.samples().length > 0) send(player, new GhostPayload(track.id, fpv, ghost.samples()));
        }
    }

    private static float[] flatten(List<float[]> samples, long lapMs) {
        List<float[]> kept = new ArrayList<>();
        for (float[] s : samples) if (s[0] >= 0 && s[0] <= lapMs + 100) kept.add(s);
        float[] out = new float[kept.size() * GhostPayload.STRIDE];
        for (int i = 0; i < kept.size(); i++) System.arraycopy(kept.get(i), 0, out, i * GhostPayload.STRIDE, GhostPayload.STRIDE);
        return out;
    }

    // ------------------------------------------------------------------ editing

    public static void onEdit(ServerPlayer player, TrackEditPayload msg) {
        if (store == null) return;
        if (!canEdit(player)) {
            player.sendSystemMessage(Component.translatable("race.freefpv.no_permission").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = player.level();
        String dim = dimension(level);
        Track track = store.track(msg.trackId());
        switch (msg.action()) {
            case PLACE_GATE -> {
                if (!Double.isFinite(msg.x()) || !Double.isFinite(msg.y()) || !Double.isFinite(msg.z())) return;
                if (player.distanceToSqr(msg.x(), msg.y(), msg.z()) > 96 * 96) return;
                if (track == null || !track.dimension.equals(dim)) {
                    if (store.tracks.size() >= MAX_TRACKS) return;
                    track = new Track();
                    track.dimension = dim;
                    track.name = "Track " + (store.tracks.size() + 1);
                    store.tracks.add(track);
                }
                if (track.gates.size() >= MAX_GATES) return;
                GateType type = track.gates.isEmpty() ? GateType.START : GateType.parse(msg.gateType(), GateType.STANDARD);
                if (type == GateType.START && !track.gates.isEmpty()) type = GateType.STANDARD;
                Gate gate = new Gate(type, msg.x(), msg.y(), msg.z(), wrap(msg.yaw()), clampScale(msg.scale()));
                track.gates.add(gate);
                spawnGate(level, gate);
                if (track.gates.size() == 1) refreshBoard(track);
                store.clearGhosts(track.id);
            }
            case UPDATE_GATE -> {
                if (track == null) return;
                Gate gate = track.gate(msg.gateId());
                if (gate == null) return;
                int from = track.indexOf(gate.id);
                if (from > 0) {
                    GateType type = GateType.parse(msg.gateType(), gate.type);
                    if (type != GateType.START) gate.type = type;
                    int to = Math.max(1, Math.min(track.gates.size() - 1, msg.index()));
                    if (msg.index() >= 0 && to != from) {
                        track.gates.remove(from);
                        track.gates.add(to, gate);
                    }
                }
                gate.scale = clampScale(msg.scale());
                applyGate(gate);
                store.clearGhosts(track.id);
            }
            case REMOVE_GATE -> {
                if (track == null) return;
                Gate gate = track.gate(msg.gateId());
                if (gate == null) return;
                track.gates.remove(gate);
                removeEntity(gate.id);
                if (!track.gates.isEmpty() && track.gates.get(0).type != GateType.START) {
                    track.gates.get(0).type = GateType.START;
                    applyGate(track.gates.get(0));
                }
                if (track.gates.isEmpty()) removeTrack(track);
                else refreshBoard(track);
                store.clearGhosts(track.id);
            }
            case UPDATE_TRACK -> {
                if (track == null) return;
                String name = msg.name().strip();
                if (!name.isEmpty()) track.name = name.length() > 32 ? name.substring(0, 32) : name;
                if (msg.laps() > 0) track.laps = Math.min(99, msg.laps());
                refreshBoard(track);
            }
            case REMOVE_TRACK -> {
                if (track != null) removeTrack(track);
            }
            case CLEAR_RECORDS -> {
                if (track == null) return;
                track.records.clear();
                store.clearGhosts(track.id);
                refreshBoard(track);
            }
        }
        store.save();
        broadcastTracks(dim);
    }

    private static void removeTrack(Track track) {
        for (Gate g : track.gates) removeEntity(g.id);
        removeEntity(BOARD_TAG + track.id);
        store.tracks.remove(track);
        store.clearGhosts(track.id);
    }

    public static boolean canEdit(ServerPlayer player) {
        if (!DroneServer.config().allows(EDITOR_TOOL) || !player.isCreative()) return false;
        return !DroneServer.config().trackEditingNeedsOp || player.canUseGameMasterBlocks();
    }

    private static float clampScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.5f, Math.min(3f, scale)) : 1f;
    }

    private static float wrap(float yaw) {
        if (!Float.isFinite(yaw)) return 0;
        float y = yaw % 360;
        return y < 0 ? y + 360 : y;
    }

    // ------------------------------------------------------------------ world entities

    private static void spawnGate(ServerLevel level, Gate gate) {
        Display.ItemDisplay entity = new Display.ItemDisplay(/*? if >=26.2 {*/EntityTypes/*?} else {*//*EntityType*//*?}*/.ITEM_DISPLAY, level);
        entity.addTag(GATE_TAG + gate.id);
        entity.setPos(gate.x, gate.y, gate.z);
        ((DisplayAccessor) entity).freefpv$setViewRange(4f);
        ((DisplayAccessor) entity).freefpv$setBrightnessOverride(Brightness.FULL_BRIGHT);
        styleGate(entity, gate);
        add(level, entity, gate.id);
    }

    private static void styleGate(Display.ItemDisplay entity, Gate gate) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, FreeFpv.id("gate_" + gate.type.id));
        ((ItemDisplayAccessor) entity).freefpv$setItemStack(stack);
        float s = gate.scale * MODEL_SCALE;
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(-gate.yaw));
        // Item displays centre the model; lift it so the model's base sits on the entity position.
        Vector3f lift = new Vector3f(0, gate.type.horizontal() ? 0 : 0.5f * s, 0);
        ((DisplayAccessor) entity).freefpv$setTransformation(new Transformation(lift, rotation, new Vector3f(s), null));
    }

    private static void applyGate(Gate gate) {
        if (ENTITIES.get(gate.id) instanceof Display.ItemDisplay entity && !entity.isRemoved()) styleGate(entity, gate);
    }

    private static void refreshBoard(Track track) {
        if (server == null || track.gates.isEmpty()) return;
        String key = BOARD_TAG + track.id;
        Gate start = track.gates.get(0);
        Display board = ENTITIES.get(key);
        if (board == null || board.isRemoved()) {
            ServerLevel level = levelOf(track.dimension);
            if (level == null || !level.isLoaded(net.minecraft.core.BlockPos.containing(start.x, start.y, start.z))) return;
            Display.TextDisplay text = new Display.TextDisplay(/*? if >=26.2 {*/EntityTypes/*?} else {*//*EntityType*//*?}*/.TEXT_DISPLAY, level);
            text.addTag(key);
            ((DisplayAccessor) text).freefpv$setBillboardConstraints(Display.BillboardConstraints.CENTER);
            ((DisplayAccessor) text).freefpv$setViewRange(2f);
            ((TextDisplayAccessor) text).freefpv$setLineWidth(220);
            ((TextDisplayAccessor) text).freefpv$setBackgroundColor(0x90101418);
            text.setPos(start.x, boardHeight(start), start.z);
            add(level, text, key);
            board = text;
        }
        board.setPos(start.x, boardHeight(start), start.z);
        ((TextDisplayAccessor) board).freefpv$setText(boardText(track));
    }

    private static double boardHeight(Gate start) {
        return start.y + (start.type.lift + start.type.height) * start.scale + 1.2;
    }

    private static Component boardText(Track track) {
        MutableComponent text = Component.literal(track.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        text.append(Component.literal("\n" + Math.max(1, track.laps) + " × " + track.gates.size()).withStyle(ChatFormatting.GRAY));
        text.append(Component.literal("  ").append(Component.translatable("race.freefpv.board_hint")).withStyle(ChatFormatting.GRAY));
        if (track.records.isEmpty()) {
            text.append(Component.literal("\n").append(Component.translatable("race.freefpv.no_records")).withStyle(ChatFormatting.DARK_GRAY));
        }
        for (int i = 0; i < track.records.size(); i++) {
            Track.Record r = track.records.get(i);
            ChatFormatting color = i == 0 ? ChatFormatting.YELLOW : i == 1 ? ChatFormatting.WHITE : i == 2 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            text.append(Component.literal("\n" + (i + 1) + ". " + r.name + "  " + RaceTime.format(r.totalMs)).withStyle(color));
        }
        return text;
    }

    private static void add(ServerLevel level, Display entity, String key) {
        spawning = entity;
        try {
            if (level.addFreshEntity(entity)) ENTITIES.put(key, entity);
        } finally {
            spawning = null;
        }
    }

    private static void removeEntity(String key) {
        Display entity = ENTITIES.remove(key);
        if (entity != null) entity.discard();
    }

    /**
     * Called whenever a display entity joins a level (spawned or loaded with its chunk). Gates and boards whose
     * track data is gone are refused; the rest are brought up to date and remembered. Returns false to refuse.
     */
    public static boolean onEntityLoad(Entity entity) {
        if (!(entity instanceof Display display) || entity == spawning) return true;
        for (String tag : tags(entity)) {
            if (tag.startsWith(GATE_TAG)) {
                String id = tag.substring(GATE_TAG.length());
                Gate gate = findGate(id);
                if (gate == null) return store == null;
                if (display instanceof Display.ItemDisplay item) styleGate(item, gate);
                display.setPos(gate.x, gate.y, gate.z);
                ENTITIES.put(id, display);
                return true;
            }
            if (tag.startsWith(BOARD_TAG)) {
                String id = tag.substring(BOARD_TAG.length());
                Track track = store == null ? null : store.track(id);
                if (track == null) return store == null;
                ENTITIES.put(BOARD_TAG + id, display);
                if (track.gates.isEmpty()) return false;
                Gate start = track.gates.get(0);
                display.setPos(start.x, boardHeight(start), start.z);
                ((TextDisplayAccessor) display).freefpv$setText(boardText(track));
                return true;
            }
        }
        return true;
    }

    private static Iterable<String> tags(Entity entity) {
        return entity./*? if >=26.1 {*/entityTags/*?} else {*//*getTags*//*?}*/();
    }

    private static Gate findGate(String id) {
        if (store == null) return null;
        for (Track t : store.tracks) {
            Gate g = t.gate(id);
            if (g != null) return g;
        }
        return null;
    }

    // ------------------------------------------------------------------ sync

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Platform.INSTANCE.sendToPlayer(player, payload);
    }

    public static void sendTracks(ServerPlayer player) {
        if (store == null) return;
        String dim = dimension(player.level());
        List<Track> list = new ArrayList<>();
        // Copies: the payload is encoded on the network thread while the server may already edit the tracks.
        for (Track t : store.tracks) if (t.dimension.equals(dim)) list.add(t.copy());
        send(player, new TracksPayload(list));
    }

    private static void broadcastTracks(String dimension) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (dimension(p.level()).equals(dimension)) sendTracks(p);
        }
    }

    private static ServerLevel levelOf(String dimension) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) if (dimension(level).equals(dimension)) return level;
        return null;
    }

    private static String dimension(net.minecraft.world.level.Level level) {
        return level.dimension()./*? if >=1.21.11 {*/identifier/*?} else {*//*location*//*?}*/().toString();
    }
}
