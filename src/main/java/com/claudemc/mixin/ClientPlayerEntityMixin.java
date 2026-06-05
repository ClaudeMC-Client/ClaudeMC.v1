package com.claudemc.mixin;

import com.claudemc.module.impl.movement.NoFall;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    /**
     * NoFall — second-layer coverage: spoof onGround=true in the regular movement
     * packet emitted by sendMovementPackets, so even the positional update carries the flag.
     * The primary layer is NoFall.onTick() which sends a dedicated OnGroundOnly packet.
     * MC's physics reconciles onGround via collision detection on the next tick,
     * so temporarily setting it here does not break physics.
     */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void claudemc$noFallPacket(CallbackInfo ci) {
        if (NoFall.INSTANCE == null || !NoFall.INSTANCE.isEnabled()) return;
        var player = (ClientPlayerEntity)(Object)this;
        if (player.isGliding() || player.getAbilities().flying) return;
        if (!player.isOnGround() && player.fallDistance > 0.0f) {
            player.setOnGround(true);
        }
    }
}
