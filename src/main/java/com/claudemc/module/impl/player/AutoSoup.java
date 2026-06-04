package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Automatically eats Mushroom Stew when health is low.
 */
public class AutoSoup extends Module {

    public static AutoSoup INSTANCE;

    public AutoSoup() {
        super("AutoSoup", "Automatically eats mushroom soup when low health", Category.PLAYER);
        addNumber("HealthThreshold", 15, 1, 20, 0.5, false);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        double thresh = parseDouble(getSetting("HealthThreshold"), 15.0);
        if (client.player.getHealth() > (float) thresh) return;

        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && isSoup(stack)) {
                inv.setSelectedSlot(i);
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                return;
            }
        }
        // Also check off-hand
        ItemStack offhand = client.player.getOffHandStack();
        if (!offhand.isEmpty() && isSoup(offhand)) {
            client.interactionManager.interactItem(client.player, Hand.OFF_HAND);
        }
    }

    private boolean isSoup(ItemStack stack) {
        var item = stack.getItem();
        return item == Items.MUSHROOM_STEW
            || item == Items.BEETROOT_SOUP
            || item == Items.RABBIT_STEW;
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
