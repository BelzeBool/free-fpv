package com.belzebool.freefpv.client.tools;

import com.belzebool.freefpv.client.DroneController;
import com.belzebool.freefpv.core.FlightMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Remote for one drone type: right click launches it, left click picks the flight mode it starts in. */
public final class DroneRemoteTool implements Multitool {
    public static final String CAMERA_ID = "freefpv:camera_remote";
    public static final String FPV_ID = "freefpv:fpv_radio";

    private final boolean fpv;

    public DroneRemoteTool(boolean fpv) {
        this.fpv = fpv;
    }

    public boolean fpv() {
        return fpv;
    }

    @Override
    public String id() {
        return fpv ? FPV_ID : CAMERA_ID;
    }

    @Override
    public String icon() {
        return fpv ? "freefpv:remote_fpv" : "freefpv:remote_camera";
    }

    @Override
    public Component name() {
        return Component.translatable(fpv ? "tool.freefpv.fpv_radio" : "tool.freefpv.camera_remote");
    }

    @Override
    public Component description() {
        return Component.translatable(fpv ? "tool.freefpv.fpv_radio.desc" : "tool.freefpv.camera_remote.desc");
    }

    @Override
    public String status() {
        return Component.translatable("tool.freefpv.mode", mode().displayName).getString();
    }

    private FlightMode mode() {
        return DroneController.INSTANCE.launchMode(fpv);
    }

    @Override
    public void use(Minecraft mc) {
        DroneController.INSTANCE.start(mc, fpv);
    }

    @Override
    public void secondary(Minecraft mc) {
        DroneController.INSTANCE.cycleLaunchMode(fpv);
        ToolSlot.INSTANCE.showName();
    }
}
