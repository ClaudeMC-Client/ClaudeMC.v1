package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class AutoEat extends Module {

    public AutoEat() {
        super("AutoEat", "Automatically eats food when hungry", Category.PLAYER);
        addSetting("Threshold", "16");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        int threshold = parseInt(getSetting("Threshold"), 16);
        if (client.player.getHungerManager().getFoodLevel() > threshold) return;

        if (tryEat(client, client.player.getMainHandStack(), Hand.MAIN_HAND)) return;
        if (tryEat(client, client.player.getOffHandStack(), Hand.OFF_HAND)) return;

        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (isFood(s)) {
                client.player.getInventory().selectedSlot = i;
                tryEat(client, s, Hand.MAIN_HAND);
                return;
            }
        }
    }

    private boolean tryEat(MinecraftClient client, ItemStack stack, Hand hand) {
        if (stack.isEmpty() || !isFood(stack)) return false;
        client.interactionManager.interactItem(client.player, hand);
        return true;
    }

    private boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}
