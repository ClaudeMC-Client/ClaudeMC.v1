package com.claudemc.mixin;

import com.claudemc.module.impl.player.FastPlace;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class FastPlaceMixin {

    @Shadow private int blockBreakingCooldown;

    /** Resets the block-breaking cooldown before each interaction when FastPlace is active. */
    @Inject(method = "interactBlock", at = @At("HEAD"), require = 0)
    private void claudemc$fastPlace(
            net.minecraft.client.network.ClientPlayerEntity player,
            Hand hand,
            net.minecraft.util.hit.BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir) {
        if (FastPlace.INSTANCE != null && FastPlace.INSTANCE.isEnabled()) {
            blockBreakingCooldown = 0;
        }
    }
}
