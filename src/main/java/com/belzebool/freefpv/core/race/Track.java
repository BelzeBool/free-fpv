package com.belzebool.freefpv.core.race;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A race track: ordered gates, the first one being start and finish, plus its records. */
public final class Track {
    public static final int MAX_RECORDS = 10;

    public String id = UUID.randomUUID().toString();
    public String name = "Track";
    /** Dimension id, "minecraft:overworld". */
    public String dimension = "minecraft:overworld";
    public int laps = 3;
    public List<Gate> gates = new ArrayList<>();
    public List<Record> records = new ArrayList<>();

    public static final class Record {
        public String player = "";
        public String name = "";
        public long totalMs;
        public long bestLapMs;
        public long date;

        public Record() {
        }

        public Record(String player, String name, long totalMs, long bestLapMs, long date) {
            this.player = player;
            this.name = name;
            this.totalMs = totalMs;
            this.bestLapMs = bestLapMs;
            this.date = date;
        }
    }

    /** Independent copy, for handing to the network thread while the server keeps editing the original. */
    public Track copy() {
        Track t = new Track();
        t.id = id;
        t.name = name;
        t.dimension = dimension;
        t.laps = laps;
        for (Gate g : gates) t.gates.add(g.copy());
        for (Record r : records) t.records.add(new Record(r.player, r.name, r.totalMs, r.bestLapMs, r.date));
        return t;
    }

    public Gate gate(String gateId) {
        for (Gate g : gates) if (g.id.equals(gateId)) return g;
        return null;
    }

    public int indexOf(String gateId) {
        for (int i = 0; i < gates.size(); i++) if (gates.get(i).id.equals(gateId)) return i;
        return -1;
    }

    /** Keeps each player's best total only. Returns the place (1-based) of this run, or 0 if it didn't make the list. */
    public int submit(Record run) {
        Record old = null;
        for (Record r : records) if (r.player.equals(run.player)) old = r;
        if (old != null) {
            if (old.totalMs <= run.totalMs) {
                old.bestLapMs = Math.min(old.bestLapMs, run.bestLapMs);
                return 0;
            }
            run.bestLapMs = Math.min(old.bestLapMs, run.bestLapMs);
            records.remove(old);
        }
        records.add(run);
        records.sort(Comparator.comparingLong(r -> r.totalMs));
        while (records.size() > MAX_RECORDS) records.remove(records.size() - 1);
        int place = records.indexOf(run);
        return place < 0 ? 0 : place + 1;
    }

    public long bestLapOverall() {
        long best = 0;
        for (Record r : records) if (r.bestLapMs > 0 && (best == 0 || r.bestLapMs < best)) best = r.bestLapMs;
        return best;
    }
}
