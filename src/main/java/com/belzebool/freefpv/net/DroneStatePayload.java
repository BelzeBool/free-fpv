package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Pilot to server, 20 times a second while flying: where the drone is so other players can see it.
 * {@code flags} uses the {@link DroneInfoPayload} bits (crashed, autopilot).
 */
public record DroneStatePayload(boolean active, boolean fpv, double x, double y, double z,
                                float qx, float qy, float qz, float qw, float motor, int flags) implements CustomPacketPayload {
    public static final Type<DroneStatePayload> TYPE = new Type<>(FreeFpv.id("drone_state"));
    public static final StreamCodec<FriendlyByteBuf, DroneStatePayload> CODEC =
        CustomPacketPayload.codec(DroneStatePayload::write, DroneStatePayload::new);

    public static DroneStatePayload inactive() {
        return new DroneStatePayload(false, false, 0, 0, 0, 0, 0, 0, 1, 0, 0);
    }

    private DroneStatePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeBoolean(fpv);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(qx);
        buf.writeFloat(qy);
        buf.writeFloat(qz);
        buf.writeFloat(qw);
        buf.writeFloat(motor);
        buf.writeByte(flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
