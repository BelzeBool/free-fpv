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
    public static final String TRACK_EDITOR = "freefpv:track_editor";
    private static final int VERSION = 2;

    /** Bumped when defaults change, so older files pick up new tools. Files from before versioning read as 0. */
    public int configVersion;

    /**
     * Tools offered in the players' tool wheel. Remove one to switch it off on this server (the server then also
     * refuses to show that drone type). Ids of tools from other mods can be listed here too.
     */
    public List<String> tools = new ArrayList<>(List.of(CAMERA_REMOTE, FPV_RADIO, TRACK_EDITOR));
    /** Longest distance a drone may fly from its pilot, in blocks. 0 = up to the client (hard cap 600). */
    public double maxRange = 0;
    /** Send drone motor load and pilot to players with the mod: real propeller sound, prop wash, pilot pose. */
    public boolean shareTelemetry = true;
    /**
     * Race tracks can be built by any player in creative mode. Set to true to also require operator rights
     * (permission level 2), for servers where builders are in creative.
     */
    public boolean trackEditingNeedsOp = false;

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
        if (config == null) {
            config = new ServerConfig();
            config.configVersion = VERSION;
        }
        if (config.tools == null) config.tools = new ArrayList<>(List.of(CAMERA_REMOTE, FPV_RADIO, TRACK_EDITOR));
        if (config.configVersion < 2 && !config.tools.contains(TRACK_EDITOR)) config.tools.add(TRACK_EDITOR);
        config.configVersion = VERSION;
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
