package com.belzebool.freefpv.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Race leaderboards are vanilla text displays; their setters are private. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void freefpv$setText(Component text);

    @Invoker("setLineWidth")
    void freefpv$setLineWidth(int width);

    @Invoker("setBackgroundColor")
    void freefpv$setBackgroundColor(int color);
}
