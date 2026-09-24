package com.belzebool.freefpv.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/** The few loader-specific services the shared code needs. */
public interface Platform {
    Platform INSTANCE =
        /*? if fabric {*/new FabricEntry.FabricPlatform();
        /*?} else *///new NeoForgeEntry.NeoPlatform();

    Path configDir();

    Path gameDir();

    boolean isModLoaded(String id);

    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
