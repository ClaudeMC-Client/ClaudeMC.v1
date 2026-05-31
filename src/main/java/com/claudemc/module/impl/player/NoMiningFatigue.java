package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Removes the Mining Fatigue status effect client-side every tick.
 * Useful on servers that apply it to protect beacons/vaults.
 */
public class NoMiningFatigue extends Module {

    public NoMiningFatigue() {
        super("NoMiningFatigue", "Removes Mining Fatigue effect client-side", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            client.player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        }
    }
}
