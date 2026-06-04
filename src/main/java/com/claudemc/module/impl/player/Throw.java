package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Automatically throws items (snowballs, eggs, ender pearls, potions) from the inventory.
 */
public class Throw extends Module {

    public static Throw INSTANCE;

    private int cooldown = 0;

    public Throw() {
        super("Throw", "Auto-throws throwable items from inventory", Category.PLAYER);
        addBool("Snowballs",    false);
        addBool("Eggs",         false);
        addBool("EnderPearls",  false);
        addBool("SplashPotion", false);
        addNumber("Delay", 10, 1, 100, 1, true);
        INSTANCE = this;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        int delay = parseInt(getSetting("Delay"), 10);
        if (cooldown-- > 0) return;

        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            var item = stack.getItem();

            boolean shouldThrow = (bool("Snowballs")    && item == Items.SNOWBALL)
                               || (bool("Eggs")         && item == Items.EGG)
                               || (bool("EnderPearls")  && item == Items.ENDER_PEARL)
                               || (bool("SplashPotion") && item == Items.SPLASH_POTION);

            if (shouldThrow) {
                inv.setSelectedSlot(i);
                client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
                cooldown = delay;
                return;
            }
        }
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
