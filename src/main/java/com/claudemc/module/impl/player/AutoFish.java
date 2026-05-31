package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.Hand;

public class AutoFish extends Module {

    private int recastDelay = 0;

    public AutoFish() {
        super("AutoFish", "Automatically reels in and recasts the fishing rod", Category.PLAYER);
        addNumber("RecastDelay", 5, 1, 20, 1, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        if (!isHoldingRod(client)) {
            int rodSlot = findRodSlot(client);
            if (rodSlot == -1) return;
            client.player.getInventory().selectedSlot = rodSlot;
            return;
        }

        if (--recastDelay > 0) return;

        FishingBobberEntity bobber = client.player.fishHook;

        if (bobber != null && hasFishBitten(client, bobber)) {
            // Reel in
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            recastDelay = parseInt(getSetting("RecastDelay"), 5) + 2;
        } else if (bobber == null) {
            // Cast
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            recastDelay = parseInt(getSetting("RecastDelay"), 5);
        }
    }

    private boolean hasFishBitten(MinecraftClient client, FishingBobberEntity bobber) {
        return bobber.getVelocity().y < -0.1 && bobber.isSubmergedInWater();
    }

    private boolean isHoldingRod(MinecraftClient client) {
        return client.player.getMainHandStack().getItem() instanceof FishingRodItem;
    }

    private int findRodSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() instanceof FishingRodItem) return i;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        recastDelay = 0;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
