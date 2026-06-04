package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Cancels configured harmful potion effects client-side each tick.
 */
public class AntiPotion extends Module {

    public static AntiPotion INSTANCE;

    public AntiPotion() {
        super("AntiPotion", "Prevents certain potion effects from applying", Category.PLAYER);
        addBool("Poison",          true);
        addBool("Blindness",       true);
        addBool("Slowness",        false);
        addBool("Weakness",        false);
        addBool("MiningFatigue",   false);
        addBool("Nausea",          false);
        addBool("Wither",          true);
        addBool("Hunger",          false);
        addBool("Levitation",      false);
        INSTANCE = this;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        if (bool("Poison")        && client.player.hasStatusEffect(StatusEffects.POISON))
            client.player.removeStatusEffect(StatusEffects.POISON);
        if (bool("Blindness")     && client.player.hasStatusEffect(StatusEffects.BLINDNESS))
            client.player.removeStatusEffect(StatusEffects.BLINDNESS);
        if (bool("Slowness")      && client.player.hasStatusEffect(StatusEffects.SLOWNESS))
            client.player.removeStatusEffect(StatusEffects.SLOWNESS);
        if (bool("Weakness")      && client.player.hasStatusEffect(StatusEffects.WEAKNESS))
            client.player.removeStatusEffect(StatusEffects.WEAKNESS);
        if (bool("MiningFatigue") && client.player.hasStatusEffect(StatusEffects.MINING_FATIGUE))
            client.player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        if (bool("Nausea")        && client.player.hasStatusEffect(StatusEffects.NAUSEA))
            client.player.removeStatusEffect(StatusEffects.NAUSEA);
        if (bool("Wither")        && client.player.hasStatusEffect(StatusEffects.WITHER))
            client.player.removeStatusEffect(StatusEffects.WITHER);
        if (bool("Hunger")        && client.player.hasStatusEffect(StatusEffects.HUNGER))
            client.player.removeStatusEffect(StatusEffects.HUNGER);
        if (bool("Levitation")    && client.player.hasStatusEffect(StatusEffects.LEVITATION))
            client.player.removeStatusEffect(StatusEffects.LEVITATION);
    }
}
