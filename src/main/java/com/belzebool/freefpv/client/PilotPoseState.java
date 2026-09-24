package com.belzebool.freefpv.client;

/** Added to the player render state by a mixin: whether this player is flying a drone, and which kind. */
public interface PilotPoseState {
    /** 0 = not piloting, 1 = camera drone, 2 = FPV. */
    int freefpv$pilotMode();

    void freefpv$setPilotMode(int mode);
}
