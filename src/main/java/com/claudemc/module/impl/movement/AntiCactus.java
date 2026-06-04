package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * AntiCactus – prevents taking damage from cacti.
 *
 * Requires a mixin on LivingEntity.damage() (or Entity.isInvulnerableTo()) to cancel
 * cactus damage. The INSTANCE flag is checked by AntiCactusMixin.
 */
public class AntiCactus extends Module {

    public static AntiCactus INSTANCE;

    public AntiCactus() {
        super("AntiCactus", "Prevents taking damage from cacti", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Logic handled in AntiCactusMixin
    }
}
