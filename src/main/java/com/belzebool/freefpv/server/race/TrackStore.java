package com.belzebool.freefpv.server.race;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.core.race.Track;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Tracks of one world, {@code <world>/freefpv/tracks.json}, and every pilot's best lap on each track (splits for the
 * live delta plus the flight path for the ghost), {@code <world>/freefpv/ghosts/<track>/<player>.bin}.
 */
public final class TrackStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path root;
    public final List<Track> tracks = new ArrayList<>();

    public record Ghost(long lapMs, long[] splits, float[] samples) {
    }

    private TrackStore(Path root) {
        this.root = root;
    }

    public static TrackStore load(Path worldDir) {
        TrackStore store = new TrackStore(worldDir.resolve("freefpv"));
        Path file = store.root.resolve("tracks.json");
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                List<Track> loaded = GSON.fromJson(reader, new TypeToken<List<Track>>() { }.getType());
                if (loaded != null) {
                    for (Track t : loaded) {
                        if (t == null || t.id == null) continue;
                        if (t.gates == null) t.gates = new ArrayList<>();
                        if (t.records == null) t.records = new ArrayList<>();
                        t.gates.removeIf(g -> g == null || g.type == null || g.id == null);
                        store.tracks.add(t);
                    }
                }
            } catch (IOException | JsonParseException e) {
                FreeFpv.LOGGER.error("Could not read race tracks from {}: {}", file, e.getMessage());
            }
        }
        return store;
    }

    public void save() {
        try {
            Files.createDirectories(root);
            Path file = root.resolve("tracks.json");
            Path temp = root.resolve("tracks.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(tracks, writer);
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            FreeFpv.LOGGER.error("Could not save race tracks: {}", e.getMessage());
        }
    }

    public Track track(String id) {
        for (Track t : tracks) if (t.id.equals(id)) return t;
        return null;
    }

    private Path ghostFile(String trackId, String player) {
        return root.resolve("ghosts").resolve(safe(trackId)).resolve(safe(player) + ".bin");
    }

    public Ghost ghost(String trackId, String player) {
        Path file = ghostFile(trackId, player);
        if (!Files.isRegularFile(file)) return null;
        try (InputStream raw = Files.newInputStream(file); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            long lap = in.readLong();
            long[] splits = new long[in.readInt()];
            for (int i = 0; i < splits.length; i++) splits[i] = in.readLong();
            float[] samples = new float[in.readInt()];
            for (int i = 0; i < samples.length; i++) samples[i] = in.readFloat();
            return new Ghost(lap, splits, samples);
        } catch (IOException | RuntimeException e) {
            FreeFpv.LOGGER.warn("Broken ghost {}: {}", file, e.getMessage());
            return null;
        }
    }

    public void saveGhost(String trackId, String player, Ghost ghost) {
        Path file = ghostFile(trackId, player);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream raw = Files.newOutputStream(file); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
                out.writeLong(ghost.lapMs());
                out.writeInt(ghost.splits().length);
                for (long s : ghost.splits()) out.writeLong(s);
                out.writeInt(ghost.samples().length);
                for (float f : ghost.samples()) out.writeFloat(f);
            }
        } catch (IOException e) {
            FreeFpv.LOGGER.warn("Could not save ghost {}: {}", file, e.getMessage());
        }
    }

    /** Drops ghosts of a track: its gates moved, so old laps no longer match. */
    public void clearGhosts(String trackId) {
        Path dir = root.resolve("ghosts").resolve(safe(trackId));
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) Files.deleteIfExists(f);
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            FreeFpv.LOGGER.warn("Could not clear ghosts of {}: {}", trackId, e.getMessage());
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
