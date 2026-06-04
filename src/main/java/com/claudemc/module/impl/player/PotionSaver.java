package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Prevents wasting potions when the player already has that effect at max amplifier.
 * Implemented by suppressing right-click interactions via a flag checked in a mixin.
 * This module exposes static helpers that the interaction layer can query.
 */
public class PotionSaver extends Module {

    public static PotionSaver INSTANCE;

    public PotionSaver() {
        super("PotionSaver", "Prevents wasting potions on already-buffed targets", Category.PLAYER);
        addBool("Strength",  true);
        addBool("Speed",     true);
        addBool("Healing",   false);
        addBool("FireResist", true);
        INSTANCE = this;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    /** Returns true if using the item held should be suppressed (potion already active). */
    public boolean shouldBlock(MinecraftClient client) {
        if (client.player == null) return false;
        var held = client.player.getMainHandStack();
        if (held.isEmpty()) return false;

        var comp = held.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        if (comp == null) return false;

        for (var inst : comp.getEffects()) {
            var type = inst.getEffectType();
            if (bool("Strength")   && type == StatusEffects.STRENGTH
                    && client.player.hasStatusEffect(StatusEffects.STRENGTH)) return true;
            if (bool("Speed")      && type == StatusEffects.SPEED
                    && client.player.hasStatusEffect(StatusEffects.SPEED)) return true;
            if (bool("Healing")    && type == StatusEffects.INSTANT_HEALTH
                    && client.player.getHealth() >= client.player.getMaxHealth()) return true;
            if (bool("FireResist") && type == StatusEffects.FIRE_RESISTANCE
                    && client.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) return true;
        }
        return false;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
