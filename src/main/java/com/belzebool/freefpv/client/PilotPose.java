package com.belzebool.freefpv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * "Holding the remote" pose without Emotecraft: both hands on the remote in front of the chest, the remote model in
 * the main hand. Camera drone pilots look down at the phone on the remote. Applies to the local pilot and, with
 * telemetry from a Free FPV server, to every other pilot seen by players who have the mod.
 */
public final class PilotPose {
    private PilotPose() {
    }

    public static int modeFor(int entityId) {
        DroneController controller = DroneController.INSTANCE;
        if (!controller.config().effects.pilotPose) return 0;
        int local = controller.localPilotMode(entityId);
        return local != 0 ? local : RemoteDrones.pilotMode(entityId);
    }

    /** Puts the remote into the pilot's main hand for rendering. */
    public static void holdRemote(LivingEntity entity, AvatarRenderState state, int mode) {
        ItemStack remote = GuiCanvas.iconStack(mode == 2 ? "freefpv:remote_fpv" : "freefpv:remote_camera");
        boolean right = state.mainArm == HumanoidArm.RIGHT;
        var resolver = Minecraft.getInstance().getItemModelResolver();
        if (right) {
            resolver.updateForLiving(state.rightHandItemState, remote, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
            state.rightHandItemStack = remote;
            state.leftHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
        } else {
            resolver.updateForLiving(state.leftHandItemState, remote, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
            state.leftHandItemStack = remote;
            state.rightHandItemState.clear();
            state.rightHandItemStack = ItemStack.EMPTY;
        }
    }

    public static void apply(HumanoidModel<?> model, int mode) {
        model.rightArm.xRot = -0.95f;
        model.rightArm.yRot = -0.42f;
        model.rightArm.zRot = 0;
        model.leftArm.xRot = -0.95f;
        model.leftArm.yRot = 0.42f;
        model.leftArm.zRot = 0;
        if (mode == 1) model.head.xRot = Math.max(model.head.xRot, 0.55f);
    }
}
