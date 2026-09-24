package com.belzebool.freefpv.platform;

//? if fabric {
import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.client.DroneController;
import com.belzebool.freefpv.client.GuiCanvas;
import com.belzebool.freefpv.client.Keys;
import com.belzebool.freefpv.client.ServerFeatures;
import com.belzebool.freefpv.client.tools.ToolSlot;
import com.belzebool.freefpv.net.DroneInfoPayload;
import com.belzebool.freefpv.net.OwnDronePayload;
import com.belzebool.freefpv.net.ServerConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

//? if >=1.21.9 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else
//import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class FabricClientEntry implements ClientModInitializer {
    /** Vanilla HUD parts hidden while looking through the drone. Chat, titles and the scoreboard stay. */
    private static final List<Identifier> HIDDEN = List.of(
        VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR,
        VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
        VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP,
        VanillaHudElements.MOB_EFFECTS, VanillaHudElements.CROSSHAIR);

    @Override
    public void onInitializeClient() {
        //? if >=1.21.9 {
        Keys.ALL.forEach(KeyMappingHelper::registerKeyMapping);
        //?} else
        //Keys.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(DroneController.INSTANCE::clientTick);
        ClientPlayNetworking.registerGlobalReceiver(OwnDronePayload.TYPE,
            (payload, context) -> DroneController.INSTANCE.setOwnDroneId(payload.entityId()));
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) -> ServerFeatures.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(DroneInfoPayload.TYPE,
            (payload, context) -> DroneController.INSTANCE.onDroneInfo(payload));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, FreeFpv.id("osd"),
            (graphics, delta) -> DroneController.INSTANCE.renderHud(new GuiCanvas(graphics)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, FreeFpv.id("tool_slot"),
            (graphics, delta) -> ToolSlot.INSTANCE.render(graphics));
        for (Identifier id : HIDDEN) {
            HudElementRegistry.replaceElement(id, original -> (graphics, delta) -> {
                if (!DroneController.INSTANCE.hideVanillaHud()) original./*? if >=26.1 {*/extractRenderState/*?} else {*//*render*//*?}*/(graphics, delta);
            });
        }

        DroneController.INSTANCE.init();
    }

    static boolean canSend(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
//?}
