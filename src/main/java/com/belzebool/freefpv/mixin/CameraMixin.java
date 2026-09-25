package com.belzebool.freefpv.mixin;

import com.belzebool.freefpv.client.DroneController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
//? if >=26.2
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the camera into the drone: position, full 3-axis rotation (including roll) and field of view. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    //? if >=26.2 {
    @Shadow @Final private static Vector3fc FORWARDS;
    @Shadow @Final private static Vector3fc UP;
    @Shadow @Final private static Vector3fc LEFT;
    //?} else {
    /*@Shadow @Final private static Vector3f FORWARDS;
    @Shadow @Final private static Vector3f UP;
    @Shadow @Final private static Vector3f LEFT;
    *///?}
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow @Final private Quaternionf rotation;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private boolean detached;
    //? if >=26.1
    @Shadow private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    //? if >=26.1 {
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    //?} else
    //@Inject(method = "setup", at = @At("TAIL"))
    private void freefpv$droneView(CallbackInfo ci) {
        DroneController drone = DroneController.INSTANCE;
        if (!drone.isFlying()) return;
        drone.onFrame(Minecraft.getInstance());
        if (!drone.isFlying()) return;

        Vector3d pos = drone.cameraPosition();
        Quaterniond rot = drone.cameraRotation();
        setPosition(new Vec3(pos.x, pos.y, pos.z));
        rotation.set((float) rot.x, (float) rot.y, (float) rot.z, (float) rot.w);
        FORWARDS.rotate(rotation, forwards);
        UP.rotate(rotation, up);
        LEFT.rotate(rotation, left);
        yRot = (float) Math.toDegrees(Math.atan2(-forwards.x, forwards.z));
        xRot = (float) -Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, forwards.y))));
        //? if >=26.1
        matrixPropertiesDirty |= 3;
        detached = true;
    }

    //? if >=26.1 {
    /**
     * A shorter near plane while flying. The drone camera sits a few centimetres from walls; with the vanilla 5 cm
     * near plane the corners of a 118 degree FPV lens would reach through them.
     */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 0)
    private float freefpv$nearPlane(float zNear) {
        return DroneController.INSTANCE.isFlying() ? DroneController.NEAR_PLANE : zNear;
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void freefpv$droneFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (DroneController.INSTANCE.isFlying()) cir.setReturnValue((float) DroneController.INSTANCE.verticalFov());
    }
    //?}
}
