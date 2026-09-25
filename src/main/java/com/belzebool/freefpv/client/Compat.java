package com.belzebool.freefpv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client accessors that moved between Minecraft versions (26.2 split the GUI into Gui and Hud). */
public final class Compat {
    private Compat() {
    }

    public static Screen screen(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else
        //return mc.screen;
    }

    /** No screen and no loading overlay: the player is looking at the world. */
    public static boolean inWorldView(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen() == null && mc.gui.overlay() == null;
        //?} else
        //return mc.screen == null && mc.getOverlay() == null;
    }

    public static boolean hudHidden(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.hud.isHidden();
        //?} else
        //return mc.options.hideGui;
    }
}
