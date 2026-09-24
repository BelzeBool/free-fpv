package com.belzebool.freefpv.client.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Something that lives in the tool slot and the tool wheel: a drone remote today, other multitools later. Tools are
 * client-side; a Free FPV server decides which ones its players get by listing their ids (see
 * {@code config/freefpv-server.json}).
 */
public interface Multitool {
    /** Stable id, "namespace:name". Servers allow or deny tools by this id. */
    String id();

    /** Item model drawn in the slot, in the wheel and in the pilot's hand, "namespace:path". */
    String icon();

    Component name();

    Component description();

    /** Short line shown under the name, such as the flight mode. */
    default String status() {
        return "";
    }

    /** A few characters in the corner of the slot. */
    default String badge() {
        return "";
    }

    /**
     * Built-in tools work on any server unless a Free FPV server leaves them out. Tools that need server support
     * (add-ons) appear only when the server lists them.
     */
    default boolean needsServer() {
        return false;
    }

    /** Right click while the tool slot is active. */
    void use(Minecraft mc);

    /** Left click while the tool slot is active. */
    default void secondary(Minecraft mc) {
    }
}
