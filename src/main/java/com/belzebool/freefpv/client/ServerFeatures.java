package com.belzebool.freefpv.client;

import com.belzebool.freefpv.net.ServerConfigPayload;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * What the current server allows, from {@link ServerConfigPayload}. Tied to the connection it arrived on, so a new
 * server (or single player without the payload) never inherits the previous server's list.
 */
public final class ServerFeatures {
    private static Object connection;
    private static ServerConfigPayload config;

    private ServerFeatures() {
    }

    public static void accept(ServerConfigPayload payload) {
        connection = Minecraft.getInstance().getConnection();
        config = payload;
    }

    private static ServerConfigPayload current() {
        Object now = Minecraft.getInstance().getConnection();
        return now != null && now == connection ? config : null;
    }

    /** True when the server runs Free FPV and told us its settings. */
    public static boolean modOnServer() {
        return current() != null;
    }

    /**
     * Built-in tools work everywhere unless a Free FPV server leaves them out; tools that need the server
     * (from add-ons) only appear when the server lists them.
     */
    public static boolean allows(String toolId, boolean needsServer) {
        ServerConfigPayload c = current();
        if (c == null) return !needsServer;
        return c.tools().contains(toolId);
    }

    public static List<String> serverTools() {
        ServerConfigPayload c = current();
        return c == null ? List.of() : c.tools();
    }

    /** Server range cap in blocks, or the local value when the server sets none. */
    public static double capRange(double local) {
        ServerConfigPayload c = current();
        return c == null || c.maxRange() <= 0 ? local : Math.min(local, c.maxRange());
    }
}
