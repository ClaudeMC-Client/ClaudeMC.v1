package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Automatically uses potions (healing, speed, strength) when conditions are met.
 */
public class AutoPotion extends Module {

    public static AutoPotion INSTANCE;

    private int cooldown = 0;

    public AutoPotion() {
        super("AutoPotion", "Automatically uses potions when needed", Category.PLAYER);
        addBool("Healing",  true);
        addBool("Speed",    false);
        addBool("Strength", false);
        addNumber("HealthThreshold", 10, 1, 20, 0.5, false);
        INSTANCE = this;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (cooldown-- > 0) return;
        cooldown = 20;

        double healthThresh = parseDouble(getSetting("HealthThreshold"), 10.0);
        boolean needHeal    = bool("Healing") && client.player.getHealth() < (float) healthThresh;
        boolean needSpeed   = bool("Speed")   && !client.player.hasStatusEffect(StatusEffects.SPEED);
        boolean needStr     = bool("Strength") && !client.player.hasStatusEffect(StatusEffects.STRENGTH);

        if (!needHeal && !needSpeed && !needStr) return;

        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!isPotionDrinkable(stack)) continue;

            if (needHeal && hasEffect(stack, StatusEffects.INSTANT_HEALTH)) {
                inv.setSelectedSlot(i);
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                cooldown = 10;
                return;
            }
            if (needSpeed && hasEffect(stack, StatusEffects.SPEED)) {
                inv.setSelectedSlot(i);
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                cooldown = 10;
                return;
            }
            if (needStr && hasEffect(stack, StatusEffects.STRENGTH)) {
                inv.setSelectedSlot(i);
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                cooldown = 10;
                return;
            }
        }
    }

    private boolean isPotionDrinkable(ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION));
    }

    private boolean hasEffect(ItemStack stack, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
        PotionContentsComponent comp = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (comp == null) return false;
        for (StatusEffectInstance inst : comp.getEffects()) {
            if (inst.getEffectType() == effect) return true;
        }
        return false;
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
