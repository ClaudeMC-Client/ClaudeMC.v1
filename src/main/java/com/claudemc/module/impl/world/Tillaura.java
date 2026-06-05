package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Tillaura extends Module {

    public static Tillaura INSTANCE;

    public Tillaura() {
        super("Tillaura", "Automatically tills soil blocks around the player", Category.WORLD);
        INSTANCE = this;
        addNumber("Radius", 4, 1, 8, 1, true);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Find hoe in hotbar
        int hoeSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isHoe(stack)) {
                hoeSlot = i;
                break;
            }
        }
        if (hoeSlot < 0) return;
        mc.player.getInventory().setSelectedSlot(hoeSlot);

        int radius = parseInt(getSetting("Radius"), 4);
        BlockPos playerPos = mc.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, radius, 1, radius)) {
            var block = mc.world.getBlockState(pos).getBlock();
            // Till dirt, grass, or coarse dirt
            if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT
                    || block == Blocks.DIRT_PATH) {
                // Make sure block above is air
                if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                return;
            }
        }
    }

    private boolean isHoe(ItemStack stack) {
        return stack.getItem() == Items.WOODEN_HOE
            || stack.getItem() == Items.STONE_HOE
            || stack.getItem() == Items.IRON_HOE
            || stack.getItem() == Items.GOLDEN_HOE
            || stack.getItem() == Items.DIAMOND_HOE
            || stack.getItem() == Items.NETHERITE_HOE;
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
