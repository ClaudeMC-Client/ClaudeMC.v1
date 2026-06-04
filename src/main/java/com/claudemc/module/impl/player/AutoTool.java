package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Automatically equips the best tool for the block currently targeted.
 * Similar to AutoSwitch but always active — no switch-back behaviour.
 */
public class AutoTool extends Module {

    public static AutoTool INSTANCE;

    public AutoTool() {
        super("AutoTool", "Auto-equips best tool for targeted block", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        var blockPos   = ((BlockHitResult) hit).getBlockPos();
        var blockState = client.world.getBlockState(blockPos);

        var inv   = client.player.getInventory();
        int best  = -1;
        float spd = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            float s = stack.getMiningSpeedMultiplier(blockState);
            if (s > spd) { spd = s; best = i; }
        }

        if (best != -1) inv.setSelectedSlot(best);
    }
}
