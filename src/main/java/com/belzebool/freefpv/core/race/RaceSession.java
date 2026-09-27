package com.belzebool.freefpv.core.race;

import java.util.ArrayList;
import java.util.List;

/**
 * Timing of one pilot on one track, fed with consecutive drone positions and pilot clock times. The timer starts on
 * the first pass through the start gate (flying start) and each lap ends at the start gate again.
 */
public final class RaceSession {
    /** The drone is about 0.3 blocks wide; brushing the frame still counts. */
    private static final double MARGIN = 0.2;

    public enum Kind { START, GATE, LAP, FINISH }

    public record Event(Kind kind, int gate, long timeMs, long lapMs, long deltaMs) {
    }

    public final Track track;
    public boolean running;
    public boolean finished;
    public int lap;
    /** Index of the gate to pass next; equals the gate count when the start/finish line is next. */
    public int nextGate;
    public long raceStartMs;
    public long lapStartMs;
    public long lastLapMs;
    public long bestLapMs;
    public long totalMs;
    public final List<Long> laps = new ArrayList<>();
    /** Split times into the current lap at each gate, and the best lap's splits to compare against. */
    private final long[] splits;
    private long[] referenceSplits;

    public RaceSession(Track track, long[] referenceSplits) {
        this.track = track;
        this.splits = new long[track.gates.size() + 1];
        this.referenceSplits = referenceSplits;
    }

    public long[] splits() {
        return splits.clone();
    }

    public void setReference(long[] reference) {
        referenceSplits = reference;
    }

    /** Whether the path {@code a -> b} enters the start gate, for picking which track a pilot is racing on. */
    public static double startCrossing(Track track, double ax, double ay, double az, double bx, double by, double bz) {
        if (track.gates.isEmpty()) return -1;
        return track.gates.get(0).crossing(ax, ay, az, bx, by, bz, MARGIN);
    }

    public List<Event> update(double ax, double ay, double az, long ta, double bx, double by, double bz, long tb) {
        List<Event> events = new ArrayList<>(1);
        List<Gate> gates = track.gates;
        if (gates.isEmpty() || finished) return events;

        if (!running) {
            double t = gates.get(0).crossing(ax, ay, az, bx, by, bz, MARGIN);
            if (t < 0) return events;
            long at = ta + Math.round((tb - ta) * t);
            running = true;
            lap = 1;
            raceStartMs = lapStartMs = at;
            nextGate = gates.size() > 1 ? 1 : gates.size();
            events.add(new Event(Kind.START, 0, at, 0, 0));
            return events;
        }

        // Only the next gate counts: a skipped gate must be flown before the following ones register.
        int index = nextGate >= gates.size() ? 0 : nextGate;
        double t = gates.get(index).crossing(ax, ay, az, bx, by, bz, MARGIN);
        if (t < 0) return events;
        long at = ta + Math.round((tb - ta) * t);
        long intoLap = at - lapStartMs;

        if (index != 0) {
            splits[index] = intoLap;
            long delta = referenceSplits != null && index < referenceSplits.length && referenceSplits[index] > 0
                ? intoLap - referenceSplits[index] : 0;
            nextGate++;
            events.add(new Event(Kind.GATE, index, at, intoLap, delta));
            return events;
        }

        splits[gates.size()] = intoLap;
        long delta = referenceSplits != null && referenceSplits[gates.size()] > 0 ? intoLap - referenceSplits[gates.size()] : 0;
        lastLapMs = intoLap;
        laps.add(intoLap);
        boolean best = bestLapMs == 0 || intoLap < bestLapMs;
        if (best) bestLapMs = intoLap;
        if (lap >= Math.max(1, track.laps)) {
            finished = true;
            running = false;
            totalMs = at - raceStartMs;
            events.add(new Event(Kind.FINISH, 0, at, intoLap, delta));
        } else {
            lap++;
            lapStartMs = at;
            nextGate = gates.size() > 1 ? 1 : gates.size();
            events.add(new Event(Kind.LAP, 0, at, intoLap, delta));
        }
        return events;
    }

    /** Gate the pilot flies to next (0 = start/finish). */
    public Gate target() {
        List<Gate> gates = track.gates;
        if (gates.isEmpty()) return null;
        return gates.get(!running || nextGate >= gates.size() ? 0 : nextGate);
    }
}
