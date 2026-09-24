package com.belzebool.freefpv.platform;

//? if neoforge {
/*import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.client.DroneController;
import com.belzebool.freefpv.client.GuiCanvas;
import com.belzebool.freefpv.client.Keys;
import com.belzebool.freefpv.net.OwnDronePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

@Mod(value = FreeFpv.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeClientEntry {
    /^* Vanilla HUD layers hidden while looking through the drone. Chat, titles and the scoreboard stay. ^/
    private static final Set<Identifier> HIDDEN = Set.of(
        VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
        VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH,
        VanillaGuiLayers.CONTEXTUAL_INFO_BAR, VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
        VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.EFFECTS,
        VanillaGuiLayers.CROSSHAIR);

    public NeoForgeClientEntry(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(Keys.CATEGORY);
            Keys.ALL.forEach(event::register);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelow(VanillaGuiLayers.CHAT, FreeFpv.id("osd"),
            (graphics, delta) -> DroneController.INSTANCE.renderHud(new GuiCanvas(graphics))));
        modBus.addListener((RegisterClientPayloadHandlersEvent event) ->
            event.register(OwnDronePayload.TYPE, NeoForgeClientEntry::handleOwnDrone));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> DroneController.INSTANCE.clientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre event) -> {
            if (HIDDEN.contains(event.getName()) && DroneController.INSTANCE.hideVanillaHud()) event.setCanceled(true);
        });

        DroneController.INSTANCE.init();
    }

    static void handleOwnDrone(OwnDronePayload payload, IPayloadContext context) {
        DroneController.INSTANCE.setOwnDroneId(payload.entityId());
    }

    static boolean canSend(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
*///?}
