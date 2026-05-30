package com.claudemc.mixin;

import com.claudemc.module.impl.movement.NoFall;
import com.claudemc.module.impl.combat.Velocity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    /** NoFall: set onGround=true in the outgoing movement packet when falling */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void claudemc$noFallPacket(CallbackInfo ci) {
        var player = (ClientPlayerEntity)(Object)this;
        if (NoFall.INSTANCE != null && NoFall.INSTANCE.isEnabled()) {
            if (player.fallDistance > 2.0f) {
                // Force the player to believe it's on the ground for packet purposes
                player.setOnGround(true);
            }
        }
    }
}
