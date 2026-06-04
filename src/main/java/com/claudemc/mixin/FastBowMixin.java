package com.claudemc.mixin;

import com.claudemc.module.impl.player.FastBow;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FastBow: return maximum pull progress so the bow fires at full power immediately.
 */
@Mixin(BowItem.class)
public class FastBowMixin {

    @Inject(method = "getPullProgress", at = @At("RETURN"), cancellable = true, require = 0)
    private static void claudemc$fastBow(int useTicks, CallbackInfoReturnable<Float> cir) {
        if (FastBow.INSTANCE != null && FastBow.INSTANCE.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }
}
