package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Removes the Blindness status effect client-side every tick.
 */
public class AntiBlind extends Module {

    public static AntiBlind INSTANCE;

    public AntiBlind() {
        super("AntiBlind", "Removes blindness effect", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            client.player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }
}
