package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the racing pilot at the start of each lap: their personal best lap as samples of
 * {time ms into the lap, x, y, z, qx, qy, qz, qw}. Empty when there is no best lap yet.
 */
public record GhostPayload(String trackId, boolean fpv, float[] samples) implements CustomPacketPayload {
    public static final int STRIDE = 8;
    public static final Type<GhostPayload> TYPE = new Type<>(FreeFpv.id("ghost"));
    public static final StreamCodec<FriendlyByteBuf, GhostPayload> CODEC =
        CustomPacketPayload.codec(GhostPayload::write, GhostPayload::new);

    private GhostPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(64), buf.readBoolean(), readSamples(buf));
    }

    private static float[] readSamples(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 20 * 60 * 10 * STRIDE);
        float[] samples = new float[count];
        for (int i = 0; i < count; i++) samples[i] = buf.readFloat();
        return samples;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(trackId, 64);
        buf.writeBoolean(fpv);
        buf.writeVarInt(samples.length);
        for (float f : samples) buf.writeFloat(f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
