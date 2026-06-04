package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Switches to the best tool for the block currently being looked at / mined.
 */
public class AutoSwitch extends Module {

    public static AutoSwitch INSTANCE;

    private int prevSlot = -1;

    public AutoSwitch() {
        super("AutoSwitch", "Switches to best tool for block being mined", Category.PLAYER);
        addBool("SwitchBack", true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            // Restore slot if mining stopped
            if (prevSlot != -1 && Boolean.parseBoolean(getSetting("SwitchBack"))) {
                client.player.getInventory().setSelectedSlot(prevSlot);
                prevSlot = -1;
            }
            return;
        }

        var blockPos   = ((BlockHitResult) hit).getBlockPos();
        var blockState = client.world.getBlockState(blockPos);
        Block block    = blockState.getBlock();

        int bestSlot = getBestToolSlot(client, block, blockState);
        if (bestSlot != -1 && bestSlot != client.player.getInventory().getSelectedSlot()) {
            if (prevSlot == -1) prevSlot = client.player.getInventory().getSelectedSlot();
            client.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    private int getBestToolSlot(MinecraftClient client, Block block,
                                 net.minecraft.block.BlockState state) {
        var inv = client.player.getInventory();
        int bestSlot  = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot  = i;
            }
        }
        return bestSlot;
    }
}
