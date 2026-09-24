package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to pilot: id of the display entity that shows their drone, so their own camera can skip it. */
public record OwnDronePayload(int entityId) implements CustomPacketPayload {
    public static final Type<OwnDronePayload> TYPE = new Type<>(FreeFpv.id("own_drone"));
    public static final StreamCodec<FriendlyByteBuf, OwnDronePayload> CODEC =
        CustomPacketPayload.codec(OwnDronePayload::write, OwnDronePayload::new);

    private OwnDronePayload(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
