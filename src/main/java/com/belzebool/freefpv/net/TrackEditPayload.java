package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Track editor to server. The server checks permissions and applies the change. */
public record TrackEditPayload(Action action, String trackId, String gateId, String gateType, double x, double y, double z,
                               float yaw, float scale, int index, String name, int laps) implements CustomPacketPayload {
    public static final Type<TrackEditPayload> TYPE = new Type<>(FreeFpv.id("track_edit"));
    public static final StreamCodec<FriendlyByteBuf, TrackEditPayload> CODEC =
        CustomPacketPayload.codec(TrackEditPayload::write, TrackEditPayload::new);

    public enum Action { PLACE_GATE, UPDATE_GATE, REMOVE_GATE, UPDATE_TRACK, REMOVE_TRACK, CLEAR_RECORDS }

    public static TrackEditPayload place(String trackId, String type, double x, double y, double z, float yaw, float scale) {
        return new TrackEditPayload(Action.PLACE_GATE, trackId, "", type, x, y, z, yaw, scale, -1, "", 0);
    }

    public static TrackEditPayload gate(Action action, String trackId, String gateId, String type, float scale, int index) {
        return new TrackEditPayload(action, trackId, gateId, type, 0, 0, 0, 0, scale, index, "", 0);
    }

    public static TrackEditPayload track(Action action, String trackId, String name, int laps) {
        return new TrackEditPayload(action, trackId, "", "", 0, 0, 0, 0, 1, -1, name, laps);
    }

    private TrackEditPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(Action.class), buf.readUtf(64), buf.readUtf(64), buf.readUtf(32), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readUtf(64), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(trackId, 64);
        buf.writeUtf(gateId, 64);
        buf.writeUtf(gateType, 32);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(scale);
        buf.writeVarInt(index);
        buf.writeUtf(name, 64);
        buf.writeVarInt(laps);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
