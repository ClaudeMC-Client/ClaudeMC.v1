package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Prevents levitation from moving the player upward by removing the effect client-side.
 */
public class NoLevitation extends Module {

    public static NoLevitation INSTANCE;

    public NoLevitation() {
        super("NoLevitation", "Prevents levitation effect from moving player", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            client.player.removeStatusEffect(StatusEffects.LEVITATION);
        }
    }
}
