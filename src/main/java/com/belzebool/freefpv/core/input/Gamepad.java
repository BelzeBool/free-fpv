package com.belzebool.freefpv.core.input;

import com.belzebool.freefpv.FreeFpv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

//? if >=26.3 {
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDLStdinc;

import java.nio.IntBuffer;
//?} else {
/*import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
*///?}

/**
 * Controller input through the same native library Minecraft uses for its window: SDL3 on 26.3+, GLFW before.
 * Anything with a gamepad mapping (DualSense, DualShock 4, Xbox, Switch Pro, 8BitDo) is read in the standard
 * layout; other joysticks, such as RC transmitters in USB joystick mode, are read as raw axes.
 * Must be polled from the render thread.
 */
public final class Gamepad {
    // Standard layout, numbered like GLFW. Face buttons by position: A = bottom (Cross), B = right (Circle).
    public static final int A = 0, B = 1, X = 2, Y = 3;
    public static final int LB = 4, RB = 5, BACK = 6, START = 7, GUIDE = 8, L3 = 9, R3 = 10;
    public static final int DPAD_UP = 11, DPAD_RIGHT = 12, DPAD_DOWN = 13, DPAD_LEFT = 14;
    private static final int BUTTONS = 15;

    public static final int LEFT_X = 0, LEFT_Y = 1, RIGHT_X = 2, RIGHT_Y = 3, LEFT_TRIGGER = 4, RIGHT_TRIGGER = 5;

    public enum Kind { NONE, GAMEPAD, JOYSTICK }

    private final float[] axes = new float[8];
    private final boolean[] buttons = new boolean[BUTTONS];
    private final boolean[] previous = new boolean[BUTTONS];
    private final Backend backend = /*? if >=26.3 {*/new SdlBackend();/*?} else {*//*new GlfwBackend();*//*?}*/
    private boolean mappingsLoaded;
    private long lastActivityNanos;
    private long nextScanNanos;

    /** Loads extra SDL-format mappings (gamecontrollerdb.txt lines) once, for controllers that aren't recognised. */
    public void loadMappings(Path file) {
        if (mappingsLoaded) return;
        mappingsLoaded = true;
        try {
            if (!Files.isRegularFile(file)) return;
            List<String> lines = Files.readAllLines(file);
            int added = backend.addMappings(lines);
            if (added > 0) FreeFpv.LOGGER.info("Loaded {} controller mappings from {}", added, file);
        } catch (Exception e) {
            FreeFpv.LOGGER.warn("Could not load controller mappings {}: {}", file, e.getMessage());
        }
    }

    /** Reads the current controller. Returns false when nothing is connected. */
    public boolean poll() {
        System.arraycopy(buttons, 0, previous, 0, BUTTONS);
        boolean connected = backend.connected();
        if (!connected && System.nanoTime() >= nextScanNanos) {
            nextScanNanos = System.nanoTime() + 1_000_000_000L;
            try {
                connected = backend.open();
            } catch (Throwable t) {
                FreeFpv.LOGGER.warn("Controller support unavailable: {}", t.toString());
                nextScanNanos = Long.MAX_VALUE;
            }
            if (connected) FreeFpv.LOGGER.info("Using controller '{}' ({})", backend.name(), backend.kind() == Kind.GAMEPAD ? "gamepad" : "raw joystick");
        }
        Arrays.fill(axes, 0);
        Arrays.fill(buttons, false);
        if (!connected) return false;
        backend.read(axes, buttons);
        if (isActive()) lastActivityNanos = System.nanoTime();
        return true;
    }

    private boolean isActive() {
        for (int i = 0; i < 4; i++) if (Math.abs(axes[i]) > 0.25f) return true;
        for (boolean b : buttons) if (b) return true;
        return false;
    }

    /** True if the controller was touched within the given time. Used to ignore mouse look while flying with a pad. */
    public boolean recentlyUsed(double seconds) {
        return kind() != Kind.NONE && System.nanoTime() - lastActivityNanos < (long) (seconds * 1e9);
    }

    public Kind kind() {
        return backend.connected() ? backend.kind() : Kind.NONE;
    }

    public String name() {
        return backend.name();
    }

    public float axis(int index) {
        return index >= 0 && index < axes.length ? axes[index] : 0;
    }

    public boolean down(int button) {
        return button >= 0 && button < BUTTONS && buttons[button];
    }

    public boolean pressed(int button) {
        return down(button) && !previous[button];
    }

    /** Rumble, 0..1 per motor. Only on SDL builds; GLFW has no rumble. */
    public void rumble(double low, double high, int millis) {
        if (backend.connected()) backend.rumble(low, high, millis);
    }

    /** Light bar colour on DualSense / DualShock 4. */
    public void light(int rgb) {
        if (backend.connected()) backend.light(rgb);
    }

    /** Removes the deadzone and applies expo so small corrections stay precise. */
    public static double shape(double value, double deadzone, double expo) {
        double abs = Math.abs(value);
        if (abs <= deadzone) return 0;
        double scaled = Math.min(1, (abs - deadzone) / (1 - deadzone));
        double curved = scaled * (1 - expo) + scaled * scaled * scaled * expo;
        return Math.copySign(curved, value);
    }

    private interface Backend {
        boolean open();

        boolean connected();

        Kind kind();

        String name();

        void read(float[] axes, boolean[] buttons);

        int addMappings(List<String> lines);

        void rumble(double low, double high, int millis);

        void light(int rgb);
    }

    //? if >=26.3 {
    private static final class SdlBackend implements Backend {
        private static final int[] BUTTON_MAP = new int[BUTTONS];

        static {
            BUTTON_MAP[A] = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
            BUTTON_MAP[B] = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;
            BUTTON_MAP[X] = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;
            BUTTON_MAP[Y] = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;
            BUTTON_MAP[LB] = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;
            BUTTON_MAP[RB] = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;
            BUTTON_MAP[BACK] = SDLGamepad.SDL_GAMEPAD_BUTTON_BACK;
            BUTTON_MAP[START] = SDLGamepad.SDL_GAMEPAD_BUTTON_START;
            BUTTON_MAP[GUIDE] = SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE;
            BUTTON_MAP[L3] = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK;
            BUTTON_MAP[R3] = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK;
            BUTTON_MAP[DPAD_UP] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP;
            BUTTON_MAP[DPAD_RIGHT] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT;
            BUTTON_MAP[DPAD_DOWN] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN;
            BUTTON_MAP[DPAD_LEFT] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT;
        }

        private boolean initialised;
        private long gamepad;
        private long joystick;
        private String name = "";

        private void init() {
            if (initialised) return;
            initialised = true;
            // Minecraft only starts SDL video; controllers need their own subsystem.
            if ((SDLInit.SDL_WasInit(SDLInit.SDL_INIT_GAMEPAD) & SDLInit.SDL_INIT_GAMEPAD) == 0
                && !SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
                FreeFpv.LOGGER.warn("SDL gamepad subsystem failed to start");
            }
        }

        @Override
        public boolean open() {
            init();
            close();
            IntBuffer ids = SDLJoystick.SDL_GetJoysticks();
            if (ids == null) return false;
            int firstJoystick = -1;
            try {
                for (int i = 0; i < ids.remaining(); i++) {
                    int id = ids.get(ids.position() + i);
                    if (SDLGamepad.SDL_IsGamepad(id)) {
                        gamepad = SDLGamepad.SDL_OpenGamepad(id);
                        if (gamepad != 0) {
                            String n = SDLGamepad.SDL_GetGamepadName(gamepad);
                            name = n != null ? n : "Gamepad";
                            return true;
                        }
                    } else if (firstJoystick < 0) {
                        firstJoystick = id;
                    }
                }
            } finally {
                SDLStdinc.SDL_free(ids);
            }
            if (firstJoystick >= 0) {
                joystick = SDLJoystick.SDL_OpenJoystick(firstJoystick);
                if (joystick != 0) {
                    String n = SDLJoystick.SDL_GetJoystickName(joystick);
                    name = n != null ? n : "Joystick";
                    return true;
                }
            }
            return false;
        }

        private void close() {
            if (gamepad != 0) SDLGamepad.SDL_CloseGamepad(gamepad);
            if (joystick != 0) SDLJoystick.SDL_CloseJoystick(joystick);
            gamepad = joystick = 0;
        }

        @Override
        public boolean connected() {
            return gamepad != 0 ? SDLGamepad.SDL_GamepadConnected(gamepad)
                : joystick != 0 && SDLJoystick.SDL_JoystickConnected(joystick);
        }

        @Override
        public Kind kind() {
            return gamepad != 0 ? Kind.GAMEPAD : joystick != 0 ? Kind.JOYSTICK : Kind.NONE;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void read(float[] axes, boolean[] buttons) {
            if (gamepad != 0) {
                for (int i = 0; i < 4; i++) axes[i] = Math.max(-1f, SDLGamepad.SDL_GetGamepadAxis(gamepad, i) / 32767f);
                // SDL triggers go 0..1; the rest of the mod uses the GLFW convention of -1 (released) .. 1.
                axes[LEFT_TRIGGER] = SDLGamepad.SDL_GetGamepadAxis(gamepad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) / 32767f * 2 - 1;
                axes[RIGHT_TRIGGER] = SDLGamepad.SDL_GetGamepadAxis(gamepad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) / 32767f * 2 - 1;
                for (int i = 0; i < BUTTONS; i++) buttons[i] = SDLGamepad.SDL_GetGamepadButton(gamepad, BUTTON_MAP[i]);
            } else if (joystick != 0) {
                int count = Math.min(axes.length, SDLJoystick.SDL_GetNumJoystickAxes(joystick));
                for (int i = 0; i < count; i++) axes[i] = Math.max(-1f, SDLJoystick.SDL_GetJoystickAxis(joystick, i) / 32767f);
                int buttonCount = Math.min(BUTTONS, SDLJoystick.SDL_GetNumJoystickButtons(joystick));
                for (int i = 0; i < buttonCount; i++) buttons[i] = SDLJoystick.SDL_GetJoystickButton(joystick, i);
            }
        }

        @Override
        public int addMappings(List<String> lines) {
            init();
            int added = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (SDLGamepad.SDL_AddGamepadMapping(trimmed) >= 0) added++;
            }
            return added;
        }

        @Override
        public void rumble(double low, double high, int millis) {
            if (gamepad == 0) return;
            SDLGamepad.SDL_RumbleGamepad(gamepad, toShort(low), toShort(high), millis);
        }

        @Override
        public void light(int rgb) {
            if (gamepad == 0) return;
            SDLGamepad.SDL_SetGamepadLED(gamepad, (byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb);
        }

        private static short toShort(double v) {
            return (short) (int) Math.round(Math.max(0, Math.min(1, v)) * 0xFFFF);
        }
    }
    //?} else {
    /*private static final class GlfwBackend implements Backend {
        private GLFWGamepadState state;
        private int jid = -1;
        private Kind kind = Kind.NONE;
        private String name = "";

        @Override
        public boolean open() {
            jid = -1;
            kind = Kind.NONE;
            int firstJoystick = -1;
            for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
                if (!GLFW.glfwJoystickPresent(i)) continue;
                if (GLFW.glfwJoystickIsGamepad(i)) {
                    jid = i;
                    kind = Kind.GAMEPAD;
                    String n = GLFW.glfwGetGamepadName(i);
                    name = n != null ? n : "Gamepad";
                    return true;
                }
                if (firstJoystick < 0) firstJoystick = i;
            }
            if (firstJoystick >= 0) {
                jid = firstJoystick;
                kind = Kind.JOYSTICK;
                String n = GLFW.glfwGetJoystickName(firstJoystick);
                name = n != null ? n : "Joystick";
                return true;
            }
            return false;
        }

        @Override
        public boolean connected() {
            return jid >= 0 && GLFW.glfwJoystickPresent(jid);
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void read(float[] axes, boolean[] buttons) {
            if (kind == Kind.GAMEPAD) {
                if (state == null) state = GLFWGamepadState.malloc();
                if (!GLFW.glfwGetGamepadState(jid, state)) return;
                for (int i = 0; i <= GLFW.GLFW_GAMEPAD_AXIS_LAST; i++) axes[i] = state.axes(i);
                for (int i = 0; i < BUTTONS; i++) buttons[i] = state.buttons(i) == GLFW.GLFW_PRESS;
            } else {
                FloatBuffer raw = GLFW.glfwGetJoystickAxes(jid);
                if (raw != null) for (int i = 0; i < Math.min(axes.length, raw.limit()); i++) axes[i] = raw.get(i);
                ByteBuffer rawButtons = GLFW.glfwGetJoystickButtons(jid);
                if (rawButtons != null) for (int i = 0; i < Math.min(BUTTONS, rawButtons.limit()); i++) buttons[i] = rawButtons.get(i) == GLFW.GLFW_PRESS;
            }
        }

        @Override
        public int addMappings(List<String> lines) {
            byte[] bytes = String.join("\n", lines).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length + 1);
            try {
                buffer.put(bytes).put((byte) 0).flip();
                return GLFW.glfwUpdateGamepadMappings(buffer) ? lines.size() : 0;
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }

        @Override
        public void rumble(double low, double high, int millis) {
        }

        @Override
        public void light(int rgb) {
        }
    }
    *///?}
}
