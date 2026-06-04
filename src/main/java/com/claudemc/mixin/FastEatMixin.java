package com.claudemc.mixin;

import com.claudemc.module.impl.player.FastEat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FastEat: every tick while consuming a food item, advance itemUseTimeLeft faster.
 */
@Mixin(LivingEntity.class)
public abstract class FastEatMixin {

    @Shadow protected int itemUseTimeLeft;

    @Shadow protected ItemStack activeItemStack;

    /**
     * Called each tick while an item is being used (eating, drawing a bow, etc.).
     * We reduce itemUseTimeLeft by extra ticks so food is consumed faster.
     */
    @Inject(method = "tickActiveItemStack", at = @At("HEAD"), require = 0)
    private void claudemc$fastEat(CallbackInfo ci) {
        if (FastEat.INSTANCE == null || !FastEat.INSTANCE.isEnabled()) return;

        ItemStack active = activeItemStack;
        if (active == null || active.isEmpty()) return;
        if (active.get(DataComponentTypes.FOOD) == null) return;

        int speed = FastEat.INSTANCE.getSpeed() - 1; // normal tick already decrements by 1
        if (speed > 0) itemUseTimeLeft = Math.max(0, itemUseTimeLeft - speed);
    }
}
