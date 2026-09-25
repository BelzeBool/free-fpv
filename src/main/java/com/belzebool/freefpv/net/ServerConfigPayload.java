package com.belzebool.freefpv.net;

import com.belzebool.freefpv.FreeFpv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client on join: which Free FPV features this server allows. The tool wheel shows only tools the server
 * lists, so servers can switch tools off or offer their own; {@code maxRange} caps the flight range (0 = no cap).
 */
public record ServerConfigPayload(int protocol, float maxRange, List<String> tools) implements CustomPacketPayload {
    public static final int PROTOCOL = 1;
    public static final Type<ServerConfigPayload> TYPE = new Type<>(FreeFpv.id("server_config"));
    public static final StreamCodec<FriendlyByteBuf, ServerConfigPayload> CODEC =
        CustomPacketPayload.codec(ServerConfigPayload::write, ServerConfigPayload::new);

    private ServerConfigPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readFloat(), readTools(buf));
    }

    private static List<String> readTools(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 64);
        List<String> tools = new ArrayList<>(count);
        for (int i = 0; i < count; i++) tools.add(buf.readUtf(128));
        return List.copyOf(tools);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(protocol);
        buf.writeFloat(maxRange);
        buf.writeVarInt(tools.size());
        for (String tool : tools) buf.writeUtf(tool, 128);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
