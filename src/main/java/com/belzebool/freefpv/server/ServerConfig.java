package com.belzebool.freefpv.server;

import com.belzebool.freefpv.FreeFpv;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server side settings, {@code config/freefpv-server.json}. Read when the server starts. */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String CAMERA_REMOTE = "freefpv:camera_remote";
    public static final String FPV_RADIO = "freefpv:fpv_radio";

    /**
     * Tools offered in the players' tool wheel. Remove one to switch it off on this server (the server then also
     * refuses to show that drone type). Ids of tools from other mods can be listed here too.
     */
    public List<String> tools = new ArrayList<>(List.of(CAMERA_REMOTE, FPV_RADIO));
    /** Longest distance a drone may fly from its pilot, in blocks. 0 = up to the client (hard cap 600). */
    public double maxRange = 0;
    /** Send drone motor load and pilot to players with the mod: real propeller sound, prop wash, pilot pose. */
    public boolean shareTelemetry = true;

    public boolean allows(String tool) {
        return tools.contains(tool);
    }

    public static ServerConfig load(Path file) {
        ServerConfig config = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ServerConfig.class);
            } catch (IOException | JsonParseException e) {
                FreeFpv.LOGGER.warn("Could not read {}, using defaults: {}", file, e.getMessage());
            }
        }
        if (config == null) config = new ServerConfig();
        if (config.tools == null) config.tools = new ArrayList<>(List.of(CAMERA_REMOTE, FPV_RADIO));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            FreeFpv.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
        }
        return config;
    }
}
