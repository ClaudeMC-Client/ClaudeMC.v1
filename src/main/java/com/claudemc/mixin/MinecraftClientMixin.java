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
     *
     * Pinned to the value assigned from {@code renderTickCounter.beginRenderTick(...)} via
     * INVOKE_ASSIGN rather than a bare {@code STORE ordinal=0}, so a future change to local
     * variable ordering in render() can't silently retarget us onto an unrelated int.
     * require = 0 so a method-signature change degrades (Timer no-ops) instead of crashing load.
     */
    @ModifyVariable(
        method = "render",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;beginRenderTick(JZ)I"
        ),
        ordinal = 0,
        require = 0
    )
    private int claudemc$timerModifyTicks(int original) {
        if (Timer.INSTANCE != null && Timer.INSTANCE.isEnabled()) {
            return Math.round(original * Timer.INSTANCE.getSpeed());
        }
        return original;
    }
}
