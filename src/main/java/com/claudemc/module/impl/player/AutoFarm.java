package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoFarm extends Module {

    public AutoFarm() {
        super("AutoFarm", "Auto-harvests mature crops and replants seeds nearby", Category.PLAYER);
        addNumber("Radius", 4, 1, 8, 1, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        int radius = parseInt(getSetting("Radius"), 4);
        var pPos = client.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterateOutwards(pPos, radius, 2, radius)) {
            var state = client.world.getBlockState(pos);
            Block block = state.getBlock();

            if (isMatureCrop(state)) {
                client.interactionManager.attackBlock(pos, Direction.UP);
                return;
            }

            if (block instanceof FarmlandBlock) {
                BlockPos above = pos.up();
                var aboveState = client.world.getBlockState(above);
                if (aboveState.isAir()) {
                    int seedSlot = findSeedSlot(client);
                    if (seedSlot == -1) continue;
                    client.player.getInventory().setSelectedSlot(seedSlot);
                    client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(above), Direction.UP, pos, false));
                    return;
                }
            }
        }
    }

    private boolean isMatureCrop(net.minecraft.block.BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CropBlock cb) return cb.isMature(state);
        if (b instanceof StemBlock)    return false;
        return false;
    }

    private int findSeedSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            Item item = inv.getStack(i).getItem();
            if (item == Items.WHEAT_SEEDS || item == Items.CARROT
             || item == Items.POTATO  || item == Items.BEETROOT_SEEDS
             || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS) {
                return i;
            }
        }
        return -1;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
