package com.claudemc.mixin;

import com.claudemc.module.impl.world.Timer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    /**
     * Timer: scale the number of ticks per frame by the Timer module speed.
     * Intercepts the RenderTickCounter that drives game ticking.
     */
    @ModifyVariable(
        method = "render",
        at = @At(value = "STORE"),
        ordinal = 0
    )
    private int claudemc$timerModifyTicks(int original) {
        if (Timer.INSTANCE != null && Timer.INSTANCE.isEnabled()) {
            return Math.round(original * Timer.INSTANCE.getSpeed());
        }
        return original;
    }
}
