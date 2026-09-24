package com.belzebool.freefpv.compat;

import com.belzebool.freefpv.FreeFpv;
import com.belzebool.freefpv.platform.Platform;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Plays a "holding the remote" emote while piloting when Emotecraft is installed. Emotecraft syncs emotes to other
 * players by itself. Everything goes through reflection so the mod never needs Emotecraft at runtime, and the same
 * code covers Emotecraft 2.x (KeyframeAnimation) and 3.x (PlayerAnimationLibrary Animation).
 */
public final class EmotecraftCompat {
    private static final String BUNDLED = "freefpv_pilot.json";
    private static final String BUNDLED_UUID = "5a1f7b0e-3c2d-4e8f-9a6b-f9e0d1c2b3a4";

    private static Class<?> api;
    private static boolean checked;
    private static boolean playing;

    private EmotecraftCompat() {
    }

    public static boolean available() {
        if (!checked) {
            checked = true;
            if (Platform.INSTANCE.isModLoaded("emotecraft")) {
                try {
                    api = Class.forName("io.github.kosmx.emotes.api.events.client.ClientEmoteAPI");
                } catch (ClassNotFoundException e) {
                    FreeFpv.LOGGER.warn("Emotecraft is installed but its client API was not found");
                }
            }
        }
        return api != null;
    }

    /** Copies the bundled pilot emote into {@code .minecraft/emotes} so Emotecraft picks it up on its next load. */
    public static void installBundledEmote() {
        if (!Platform.INSTANCE.isModLoaded("emotecraft")) return;
        Path target = Platform.INSTANCE.gameDir().resolve("emotes").resolve(BUNDLED);
        if (Files.exists(target)) return;
        try (InputStream in = EmotecraftCompat.class.getResourceAsStream("/assets/freefpv/emotes/" + BUNDLED)) {
            if (in == null) return;
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
            FreeFpv.LOGGER.info("Installed pilot emote to {}", target);
        } catch (Exception e) {
            FreeFpv.LOGGER.warn("Could not install pilot emote: {}", e.getMessage());
        }
    }

    public static void start(String wanted) {
        if (wanted == null || wanted.isBlank() || !available()) return;
        try {
            Object emote = find(wanted);
            if (emote == null) {
                FreeFpv.LOGGER.info("Emote '{}' not found in Emotecraft; restart the game once after installing Free FPV", wanted);
                return;
            }
            for (Method method : api.getMethods()) {
                if (method.getName().equals("playEmote") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(emote)) {
                    playing = Boolean.TRUE.equals(method.invoke(null, emote));
                    return;
                }
            }
        } catch (Throwable t) {
            FreeFpv.LOGGER.warn("Emotecraft emote failed: {}", t.toString());
        }
    }

    public static void stop() {
        if (!playing || api == null) return;
        playing = false;
        try {
            api.getMethod("stopEmote").invoke(null);
        } catch (Throwable t) {
            FreeFpv.LOGGER.warn("Emotecraft stop failed: {}", t.toString());
        }
    }

    private static Object find(String wanted) throws ReflectiveOperationException {
        Object list = api.getMethod("clientEmoteList").invoke(null);
        if (!(list instanceof Collection<?> emotes)) return null;
        String key = wanted.trim();
        Object bundled = null;
        for (Object emote : emotes) {
            String name = nameOf(emote);
            String uuid = uuidOf(emote);
            if (key.equalsIgnoreCase(name) || key.equalsIgnoreCase(uuid)) return emote;
            if (BUNDLED_UUID.equalsIgnoreCase(uuid)) bundled = emote;
        }
        return "FPV Pilot".equalsIgnoreCase(key) ? bundled : null;
    }

    private static String nameOf(Object emote) {
        Object value = call(emote, "getNameOrId");
        if (value == null) {
            Object extra = field(emote, "extraData");
            if (extra instanceof Map<?, ?> map) value = map.get("name");
        }
        return clean(value);
    }

    private static String uuidOf(Object emote) {
        Object value = call(emote, "uuid");
        if (value == null) value = call(emote, "getUuid");
        return value == null ? "" : value.toString();
    }

    /** Emote names are stored as JSON text components in newer formats: {"text":"FPV Pilot"} or a quoted string. */
    private static String clean(Object value) {
        if (value == null) return "";
        String s = value.toString().trim();
        int text = s.indexOf("\"text\"");
        if (text >= 0) {
            int start = s.indexOf('"', s.indexOf(':', text) + 1);
            int end = s.indexOf('"', start + 1);
            if (start >= 0 && end > start) return s.substring(start + 1, end);
        }
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object field(Object target, String name) {
        try {
            return target.getClass().getField(name).get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
