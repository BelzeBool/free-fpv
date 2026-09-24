package com.belzebool.freefpv.client.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All tools the client knows, in wheel order. Add-ons register theirs here during client init. */
public final class ToolRegistry {
    private static final List<Multitool> TOOLS = new ArrayList<>();

    static {
        register(new DroneRemoteTool(false));
        register(new DroneRemoteTool(true));
    }

    private ToolRegistry() {
    }

    public static void register(Multitool tool) {
        TOOLS.removeIf(t -> t.id().equals(tool.id()));
        TOOLS.add(tool);
    }

    public static List<Multitool> all() {
        return Collections.unmodifiableList(TOOLS);
    }
}
