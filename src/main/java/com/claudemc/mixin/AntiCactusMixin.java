package com.claudemc.mixin;

import com.claudemc.module.impl.movement.AntiCactus;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class AntiCactusMixin {

    /** AntiCactus: cancel cactus damage for the local player */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$antiCactus(ServerWorld world, DamageSource source, float amount,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (AntiCactus.INSTANCE == null || !AntiCactus.INSTANCE.isEnabled()) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        // Only cancel for the local player
        if ((Object) this != mc.player) return;
        if (source.isOf(DamageTypes.CACTUS)) {
            cir.setReturnValue(false);
        }
    }
}
