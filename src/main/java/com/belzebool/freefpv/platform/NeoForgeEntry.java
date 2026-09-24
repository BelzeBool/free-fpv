package com.belzebool.freefpv.platform;

//? if neoforge {
/*import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.net.DroneInfoPayload;
import com.belzebool.freefpv.net.DroneStatePayload;
import com.belzebool.freefpv.net.OwnDronePayload;
import com.belzebool.freefpv.net.ServerConfigPayload;
import com.belzebool.freefpv.server.DroneServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Path;

@Mod(FreeFpv.MOD_ID)
public class NeoForgeEntry {
    public NeoForgeEntry(IEventBus modBus, ModContainer container) {
        modBus.addListener(NeoForgeEntry::registerPayloads);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> DroneServer.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> DroneServer.onServerStarting());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> DroneServer.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) DroneServer.onJoin(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> DroneServer.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && DroneServer.isOrphan(event.getEntity())) event.setCanceled(true);
        });
        FreeFpv.LOGGER.info("Free FPV {} on Minecraft {} (NeoForge)", FreeFpv.VERSION, FreeFpv.MINECRAFT);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2").optional();
        registrar.playToServer(DroneStatePayload.TYPE, DroneStatePayload.CODEC,
            (payload, context) -> DroneServer.handleState((ServerPlayer) context.player(), payload));
        //? if >=1.21.9 {
        registrar.playToClient(OwnDronePayload.TYPE, OwnDronePayload.CODEC);
        //?} else
        //registrar.playToClient(OwnDronePayload.TYPE, OwnDronePayload.CODEC, NeoForgeClientEntry::handleOwnDrone);
        //? if >=1.21.9 {
        registrar.playToClient(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);
        //?} else
        //registrar.playToClient(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC, (payload, context) -> com.belzebool.freefpv.client.ServerFeatures.accept(payload));
        //? if >=1.21.9 {
        registrar.playToClient(DroneInfoPayload.TYPE, DroneInfoPayload.CODEC);
        //?} else
        //registrar.playToClient(DroneInfoPayload.TYPE, DroneInfoPayload.CODEC, (payload, context) -> com.belzebool.freefpv.client.DroneController.INSTANCE.onDroneInfo(payload));
    }

    public static final class NeoPlatform implements Platform {
        @Override
        public Path configDir() {
            return FMLPaths.CONFIGDIR.get();
        }

        @Override
        public Path gameDir() {
            return FMLPaths.GAMEDIR.get();
        }

        @Override
        public boolean isModLoaded(String id) {
            return ModList.get().isLoaded(id);
        }

        @Override
        public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
            return NeoForgeClientEntry.canSend(type);
        }

        @Override
        public void sendToServer(CustomPacketPayload payload) {
            NeoForgeClientEntry.send(payload);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            if (player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
*///?}
