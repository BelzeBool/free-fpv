package com.belzebool.freefpv;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FreeFpv {
    public static final String MOD_ID = "freefpv";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String VERSION = /*$ mod_version*/ "0.2.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.3";

    /** Scoreboard tag on server-side drone display entities, used to clean up leftovers after a restart. */
    public static final String DRONE_TAG = "freefpv_drone";

    private FreeFpv() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
