package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.core.race.Gate;
import com.belzebool.freefpv.core.race.GateType;
import com.belzebool.freefpv.core.race.Track;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to modded clients: every track in the player's dimension, for the gate highlight, the arrow and the editor. */
public record TracksPayload(List<Track> tracks) implements CustomPacketPayload {
    public static final Type<TracksPayload> TYPE = new Type<>(FreeFpv.id("tracks"));
    public static final StreamCodec<FriendlyByteBuf, TracksPayload> CODEC =
        CustomPacketPayload.codec(TracksPayload::write, TracksPayload::new);

    private TracksPayload(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<Track> read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 256);
        List<Track> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Track t = new Track();
            t.id = buf.readUtf(64);
            t.name = buf.readUtf(64);
            t.laps = buf.readVarInt();
            int gates = Math.min(buf.readVarInt(), 512);
            for (int g = 0; g < gates; g++) {
                Gate gate = new Gate(GateType.parse(buf.readUtf(32), GateType.STANDARD), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat(), buf.readFloat());
                gate.id = buf.readUtf(64);
                t.gates.add(gate);
            }
            int records = Math.min(buf.readVarInt(), Track.MAX_RECORDS);
            for (int r = 0; r < records; r++) {
                t.records.add(new Track.Record("", buf.readUtf(64), buf.readVarLong(), buf.readVarLong(), 0));
            }
            tracks.add(t);
        }
        return tracks;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(tracks.size());
        for (Track t : tracks) {
            buf.writeUtf(t.id, 64);
            buf.writeUtf(t.name, 64);
            buf.writeVarInt(t.laps);
            buf.writeVarInt(t.gates.size());
            for (Gate g : t.gates) {
                buf.writeUtf(g.type.id, 32);
                buf.writeDouble(g.x);
                buf.writeDouble(g.y);
                buf.writeDouble(g.z);
                buf.writeFloat(g.yaw);
                buf.writeFloat(g.scale);
                buf.writeUtf(g.id, 64);
            }
            buf.writeVarInt(t.records.size());
            for (Track.Record r : t.records) {
                buf.writeUtf(r.name, 64);
                buf.writeVarLong(r.totalMs);
                buf.writeVarLong(r.bestLapMs);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
