package com.belzebool.freefpv.platform;

//? if fabric {
import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.net.DroneInfoPayload;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.net.OwnDronePayload;
import com.belzebool.freefpv.net.ServerConfigPayload;
import com.belzebool.freefpv.server.DroneServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public class FabricEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(DroneStatePayload.TYPE, DroneStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OwnDronePayload.TYPE, OwnDronePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DroneInfoPayload.TYPE, DroneInfoPayload.CODEC);
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(DroneStatePayload.TYPE, DroneStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OwnDronePayload.TYPE, OwnDronePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DroneInfoPayload.TYPE, DroneInfoPayload.CODEC);
        *///?}
        ServerPlayNetworking.registerGlobalReceiver(DroneStatePayload.TYPE,
            (payload, context) -> DroneServer.handleState(context.player(), payload));

        ServerTickEvents.END_SERVER_TICK.register(DroneServer::tick);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> DroneServer.onServerStarting());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DroneServer.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DroneServer.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DroneServer.remove(handler.getPlayer().getUUID()));
        //? if >=26.1 {
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> !DroneServer.isOrphan(entity));
        //?} else
        //ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> { if (DroneServer.isOrphan(entity)) entity.discard(); });

        FreeFpv.LOGGER.info("Free FPV {} on Minecraft {} (Fabric)", FreeFpv.VERSION, FreeFpv.MINECRAFT);
    }

    public static final class FabricPlatform implements Platform {
        @Override
        public Path configDir() {
            return FabricLoader.getInstance().getConfigDir();
        }

        @Override
        public Path gameDir() {
            return FabricLoader.getInstance().getGameDir();
        }

        @Override
        public boolean isModLoaded(String id) {
            return FabricLoader.getInstance().isModLoaded(id);
        }

        @Override
        public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
            return FabricClientEntry.canSend(type);
        }

        @Override
        public void sendToServer(CustomPacketPayload payload) {
            FabricClientEntry.send(payload);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
        }
    }
}
//?}
