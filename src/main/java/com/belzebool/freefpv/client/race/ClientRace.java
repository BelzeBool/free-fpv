package com.belzebool.freefpv.client.race;

import com.belzebool.freefpv.core.osd.OsdState;
import com.belzebool.freefpv.core.race.Gate;
import com.belzebool.freefpv.core.race.GateType;
import com.belzebool.freefpv.core.race.RaceTime;
import com.belzebool.freefpv.core.race.Track;
import com.belzebool.freefpv.net.GhostPayload;
import com.belzebool.freefpv.net.RacePayload;
import com.belzebool.freefpv.net.TracksPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Random;

/**
 * Client side of racing: the tracks of this dimension (from the server), the live stopwatch, the arrow and particle
 * outline pointing to the next gate, the LED trail and the ghost of the best lap.
 */
public final class ClientRace {
    private static final Random RANDOM = new Random();
    private static final int NEXT_GATE = 0x3DFF6A;
    private static final int START_GATE = 0xFFFFFF;
    private static final int EDIT_GATE = 0xFFC53D;

    private static List<Track> tracks = List.of();
    private static Object connection;
    private static RacePayload race;
    private static String banner = "";
    private static String bannerSub = "";
    private static int bannerColor = 0xFFFFFFFF;
    private static double bannerTimer;
    private static long frozenTotal = -1;
    private static final GhostDrone GHOST = new GhostDrone();

    private ClientRace() {
    }

    // ------------------------------------------------------------------ packets

    public static void onTracks(TracksPayload payload) {
        tracks = List.copyOf(payload.tracks());
        connection = Minecraft.getInstance().getConnection();
    }

    public static void onRace(RacePayload p) {
        switch (p.event()) {
            case RacePayload.RESET -> {
                if (race != null && frozenTotal < 0) show(Component.translatable("race.freefpv.dnf").getString(), "", 0xFFFF4D4F);
                race = null;
                GHOST.stop();
                return;
            }
            case RacePayload.START -> show(Component.translatable("race.freefpv.go").getString(), trackName(p.trackId()), 0xFF3DDC84);
            case RacePayload.GATE -> {
                if (p.deltaMs() != 0) show("", RaceTime.delta(p.deltaMs()), p.deltaMs() <= 0 ? 0xFF3DDC84 : 0xFFFF4D4F);
            }
            case RacePayload.LAP -> show(Component.translatable("race.freefpv.lap_done", p.lap() - 1).getString() + "  " + RaceTime.format(p.lastLapMs()),
                p.deltaMs() != 0 ? RaceTime.delta(p.deltaMs()) : "", p.deltaMs() <= 0 ? 0xFF3DDC84 : 0xFFFF4D4F);
            case RacePayload.FINISH -> {
                String sub = p.place() > 0 ? Component.translatable("race.freefpv.place", p.place()).getString()
                    : Component.translatable("race.freefpv.best_lap", RaceTime.format(p.bestLapMs())).getString();
                show(Component.translatable("race.freefpv.finish").getString() + "  " + RaceTime.format(p.totalMs()), sub,
                    p.place() == 1 ? 0xFFFFD700 : 0xFFFFFFFF);
                bannerTimer = 6;
                frozenTotal = p.totalMs();
            }
            default -> {
            }
        }
        if (p.event() == RacePayload.START) frozenTotal = -1;
        race = p;
        if (p.event() == RacePayload.FINISH) GHOST.stop();
    }

    public static void onGhost(GhostPayload payload) {
        if (race != null && race.trackId().equals(payload.trackId())) GHOST.start(payload.samples(), payload.fpv(), race.lapStartMs());
    }

    private static void show(String title, String sub, int color) {
        banner = title;
        bannerSub = sub;
        bannerColor = color;
        bannerTimer = 3;
    }

    // ------------------------------------------------------------------ state

    /** Tracks of the current dimension, empty when the server has none or doesn't run the mod. */
    public static List<Track> tracks() {
        return Minecraft.getInstance().getConnection() == connection ? tracks : List.of();
    }

    private static Track track(String id) {
        for (Track t : tracks()) if (t.id.equals(id)) return t;
        return null;
    }

    private static String trackName(String id) {
        Track t = track(id);
        return t == null ? "" : t.name;
    }

    public static boolean racing() {
        return race != null && race.event() != RacePayload.FINISH;
    }

    /** Called when the pilot lands or the flight ends: the server resets the run, the HUD should follow at once. */
    public static void onFlightEnded() {
        race = null;
        GHOST.stop();
    }

    // ------------------------------------------------------------------ per frame / per tick

    /**
     * Fills the race part of the OSD. {@code clockMs} is the pilot's flight clock; the camera pose is used to project
     * the next gate onto the screen.
     */
    public static void fillOsd(OsdState osd, long clockMs, Vector3d camPos, Quaterniond camRot, double verticalFov, double aspect, double dt) {
        bannerTimer = Math.max(0, bannerTimer - dt);
        osd.raceBanner = bannerTimer > 0 ? banner : "";
        osd.raceBannerSub = bannerTimer > 0 ? bannerSub : "";
        osd.raceBannerColor = bannerColor;
        osd.raceBannerAlpha = Math.min(1, bannerTimer / 0.4);

        Gate target = null;
        if (race != null) {
            Track t = track(race.trackId());
            osd.raceActive = t != null;
            if (t == null) return;
            boolean finished = race.event() == RacePayload.FINISH;
            osd.raceTrack = t.name;
            osd.raceLap = Math.min(race.lap(), race.laps());
            osd.raceLaps = race.laps();
            osd.raceGate = Math.min(race.nextGate(), race.gateCount());
            osd.raceGates = race.gateCount();
            osd.raceLapTime = finished ? race.lastLapMs() : Math.max(0, clockMs - race.lapStartMs());
            osd.raceTotalTime = finished ? race.totalMs() : Math.max(0, clockMs - race.raceStartMs());
            osd.raceBestLap = race.bestLapMs() > 0 ? race.bestLapMs() : race.personalBestMs();
            osd.raceRecord = race.trackRecordMs();
            osd.raceFinished = finished;
            if (!finished && !t.gates.isEmpty()) {
                int index = race.nextGate() >= t.gates.size() ? 0 : race.nextGate();
                target = t.gates.get(index);
            }
        } else {
            osd.raceActive = false;
            target = nearestStart(camPos, 48);
        }
        project(osd, target, camPos, camRot, verticalFov, aspect);
    }

    private static Gate nearestStart(Vector3d pos, double range) {
        Gate best = null;
        double bestD = range * range;
        for (Track t : tracks()) {
            if (t.gates.isEmpty()) continue;
            Gate g = t.gates.get(0);
            double d = pos.distanceSquared(g.cx(), g.cy(), g.cz());
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    private static void project(OsdState osd, Gate gate, Vector3d camPos, Quaterniond camRot, double verticalFov, double aspect) {
        osd.targetVisible = gate != null;
        if (gate == null) return;
        Vector3d v = new Vector3d(gate.cx() - camPos.x, gate.cy() - camPos.y, gate.cz() - camPos.z);
        osd.targetDistance = v.length();
        new Quaterniond(camRot).conjugate().transform(v);
        double tanV = Math.tan(Math.toRadians(verticalFov) / 2), tanH = tanV * aspect;
        if (v.z < -0.01) {
            osd.targetX = (v.x / -v.z) / tanH;
            osd.targetY = (v.y / -v.z) / tanV;
            osd.targetInFront = true;
        } else {
            // Behind the camera: point the arrow at the screen edge in the gate's direction.
            double len = Math.max(1e-6, Math.hypot(v.x, v.y));
            osd.targetX = v.x / len * 2;
            osd.targetY = v.y / len * 2;
            osd.targetInFront = false;
        }
    }

    /** Client tick while flying: outline of the next gate, LED trail and the ghost. */
    public static void flyingTick(Minecraft mc, Vector3d dronePos, boolean trail, int trailColor, boolean ghost, long clockMs) {
        ClientLevel level = mc.level;
        if (level == null) return;
        Gate next = null;
        int color = NEXT_GATE;
        if (race != null && race.event() != RacePayload.FINISH) {
            Track t = track(race.trackId());
            if (t != null && !t.gates.isEmpty()) next = t.gates.get(race.nextGate() >= t.gates.size() ? 0 : race.nextGate());
        } else {
            next = nearestStart(dronePos, 64);
            color = START_GATE;
        }
        if (next != null) outline(level, next, color, 18);
        if (trail && race != null) {
            level.addParticle(dust(trailColor, 0.9f), dronePos.x, dronePos.y, dronePos.z, 0, 0, 0);
        }
        if (ghost) GHOST.tick(mc, clockMs);
        else GHOST.stop();
    }

    /** While the track editor is in hand: faint outlines of every gate, the start in white. */
    public static void editorTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.getGameTime() % 4 != 0) return;
        for (Track t : tracks()) {
            for (int i = 0; i < t.gates.size(); i++) {
                Gate g = t.gates.get(i);
                if (mc.player.distanceToSqr(g.x, g.y, g.z) > 80 * 80) continue;
                outline(level, g, i == 0 ? START_GATE : EDIT_GATE, 10);
            }
        }
    }

    private static void outline(ClientLevel level, Gate g, int color, int count) {
        double w = g.width() / 2, h = g.height() / 2;
        double nx = g.nx(), nz = g.nz();
        // across the travel direction
        double ax = nz, az = -nx;
        DustParticleOptions dust = dust(color, 1.0f);
        for (int i = 0; i < count; i++) {
            double u, v;
            double r = RANDOM.nextDouble() * 4;
            double s = RANDOM.nextDouble() * 2 - 1;
            if (r < 1) { u = -w; v = s * h; } else if (r < 2) { u = w; v = s * h; } else if (r < 3) { u = s * w; v = h; } else { u = s * w; v = -h; }
            double x, y, z;
            if (g.type.horizontal()) {
                x = g.cx() + ax * u + nx * v;
                y = g.cy() + 0.15;
                z = g.cz() + az * u + nz * v;
            } else if (g.type == GateType.FLAG) {
                x = g.cx();
                y = g.y + RANDOM.nextDouble() * 3.6 * g.scale;
                z = g.cz();
            } else {
                x = g.cx() + ax * u;
                y = g.cy() + v;
                z = g.cz() + az * u;
            }
            level.addParticle(dust, x, y, z, 0, 0, 0);
        }
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        //? if >=1.21.2 {
        return new DustParticleOptions(rgb, scale);
        //?} else
        //return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), scale);
    }

    public static void clear() {
        race = null;
        tracks = List.of();
        GHOST.stop();
    }

    /** Ray against the gates of all tracks, for the editor. Returns {track, gate} or null. */
    public static Object[] pick(Vector3d from, Vector3d dir, double range) {
        Object[] best = null;
        double bestT = range;
        for (Track t : tracks()) {
            for (Gate g : t.gates) {
                double d = hit(g, from, dir);
                if (d >= 0 && d < bestT) {
                    bestT = d;
                    best = new Object[]{t, g};
                }
            }
        }
        return best;
    }

    /** Distance along the ray to the gate's bounding box, or -1. */
    private static double hit(Gate g, Vector3d o, Vector3d d) {
        double w = g.width() / 2 + 0.4, h = g.type == GateType.FLAG ? 1.8 * g.scale : g.height() / 2 + 0.4;
        double cy = g.type == GateType.FLAG ? g.y + h : g.cy();
        double minX = g.cx() - w, maxX = g.cx() + w, minZ = g.cz() - w, maxZ = g.cz() + w;
        if (g.type == GateType.FLAG) {
            minX = g.cx() - 0.6;
            maxX = g.cx() + 0.6;
            minZ = g.cz() - 0.6;
            maxZ = g.cz() + 0.6;
        }
        double minY = cy - (g.type.horizontal() ? 0.4 : h), maxY = cy + (g.type.horizontal() ? 0.4 : h);
        double t0 = 0, t1 = Double.MAX_VALUE;
        double[] o3 = {o.x, o.y, o.z}, d3 = {d.x, d.y, d.z}, lo = {minX, minY, minZ}, hi = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d3[i]) < 1e-9) {
                if (o3[i] < lo[i] || o3[i] > hi[i]) return -1;
                continue;
            }
            double a = (lo[i] - o3[i]) / d3[i], b = (hi[i] - o3[i]) / d3[i];
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
            if (t0 > t1) return -1;
        }
        return t0;
    }
}
