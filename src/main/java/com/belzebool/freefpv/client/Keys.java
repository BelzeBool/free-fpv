package com.belzebool.freefpv.client;

import com.belzebool.freefpv.FreeFpv;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.List;

/** Key bindings, shown under "Free FPV" in Controls. Flight itself uses the vanilla movement keys. */
public final class Keys {
    public static final KeyMapping.Category CATEGORY =
        /*? if fabric {*/KeyMapping.Category.register(FreeFpv.id("main"));
        /*?} else *///new KeyMapping.Category(FreeFpv.id("main"));

    public static final KeyMapping LAUNCH = key("launch", InputConstants.KEY_B);
    public static final KeyMapping MODE = key("mode", InputConstants.KEY_N);
    public static final KeyMapping DRONE_TYPE = key("type", InputConstants.KEY_M);
    public static final KeyMapping RECORD = key("record", InputConstants.KEY_R);
    public static final KeyMapping OSD = key("osd", InputConstants.KEY_O);
    public static final KeyMapping GIMBAL_RESET = key("gimbal_reset", InputConstants.KEY_G);
    public static final KeyMapping RETURN_HOME = key("return_home", InputConstants.KEY_H);
    public static final KeyMapping HELP = key("help", InputConstants.KEY_I);
    /** Selects the 10th slot (tool slot), like the number keys select hotbar slots. */
    public static final KeyMapping TOOL_SLOT = key("tool_slot", InputConstants.KEY_0);
    /** Hold to open the radial tool wheel. */
    public static final KeyMapping TOOL_WHEEL = key("tool_wheel", InputConstants.KEY_LALT);

    public static final List<KeyMapping> ALL = List.of(LAUNCH, MODE, DRONE_TYPE, RECORD, OSD, GIMBAL_RESET, RETURN_HOME, HELP,
        TOOL_SLOT, TOOL_WHEEL);

    private Keys() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.freefpv." + name, code, CATEGORY);
    }
}
