package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to clients that have the mod, ten times a second: the drones around them with their pilot and motor load.
 * Vanilla clients just see the item display; modded ones add real motor sound, prop wash and the pilot's pose.
 */
public record DroneInfoPayload(List<Entry> drones) implements CustomPacketPayload {
    public static final Type<DroneInfoPayload> TYPE = new Type<>(FreeFpv.id("drone_info"));
    public static final StreamCodec<FriendlyByteBuf, DroneInfoPayload> CODEC =
        CustomPacketPayload.codec(DroneInfoPayload::write, DroneInfoPayload::new);

    public static final int FPV = 1, CRASHED = 2, AUTOPILOT = 4;

    public record Entry(int droneId, int pilotId, int flags, float motor) {
        public boolean fpv() {
            return (flags & FPV) != 0;
        }

        public boolean crashed() {
            return (flags & CRASHED) != 0;
        }
    }

    private DroneInfoPayload(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<Entry> read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 256);
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readFloat()));
        }
        return list;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(drones.size());
        for (Entry e : drones) {
            buf.writeVarInt(e.droneId());
            buf.writeVarInt(e.pilotId());
            buf.writeByte(e.flags());
            buf.writeFloat(e.motor());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
