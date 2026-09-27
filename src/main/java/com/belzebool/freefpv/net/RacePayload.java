package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the racing pilot on every timing event. Times are on the pilot's own flight clock (the one it sends in
 * {@link DroneStatePayload}), so the client can run the stopwatch locally without drift.
 */
public record RacePayload(String trackId, int event, int lap, int laps, int nextGate, int gateCount,
                          long raceStartMs, long lapStartMs, long lastLapMs, long bestLapMs, long personalBestMs,
                          long trackRecordMs, long deltaMs, long totalMs, int place) implements CustomPacketPayload {
    public static final Type<RacePayload> TYPE = new Type<>(FreeFpv.id("race"));
    public static final StreamCodec<FriendlyByteBuf, RacePayload> CODEC =
        CustomPacketPayload.codec(RacePayload::write, RacePayload::new);

    public static final int NONE = 0, START = 1, GATE = 2, LAP = 3, FINISH = 4, RESET = 5;

    public static RacePayload reset() {
        return new RacePayload("", RESET, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private RacePayload(FriendlyByteBuf buf) {
        this(buf.readUtf(64), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
            buf.readLong(), buf.readVarLong(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(trackId, 64);
        buf.writeByte(event);
        buf.writeVarInt(lap);
        buf.writeVarInt(laps);
        buf.writeVarInt(nextGate);
        buf.writeVarInt(gateCount);
        buf.writeVarLong(raceStartMs);
        buf.writeVarLong(lapStartMs);
        buf.writeVarLong(lastLapMs);
        buf.writeVarLong(bestLapMs);
        buf.writeVarLong(personalBestMs);
        buf.writeVarLong(trackRecordMs);
        buf.writeLong(deltaMs);
        buf.writeVarLong(totalMs);
        buf.writeVarInt(place);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
