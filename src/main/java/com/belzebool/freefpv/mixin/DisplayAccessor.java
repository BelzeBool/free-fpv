package com.belzebool.freefpv.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display setters are private in vanilla; the drone model is driven through them. */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void freefpv$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void freefpv$setTransformationInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void freefpv$setTransformationInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void freefpv$setPosRotInterpolationDuration(int ticks);

    @Invoker("setViewRange")
    void freefpv$setViewRange(float range);

    @Invoker("setShadowRadius")
    void freefpv$setShadowRadius(float radius);

    @Invoker("setShadowStrength")
    void freefpv$setShadowStrength(float strength);
}
