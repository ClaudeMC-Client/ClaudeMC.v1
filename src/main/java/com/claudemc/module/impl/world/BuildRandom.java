package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class BuildRandom extends Module {

    public static BuildRandom INSTANCE;

    private final Random random = new Random();

    public BuildRandom() {
        super("BuildRandom", "Places random blocks from hotbar near the player", Category.WORLD);
        INSTANCE = this;
        addNumber("Radius", 3, 1, 6, 1, true);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Find a slot in hotbar with a block item
        int blockSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                blockSlot = i;
                break;
            }
        }
        if (blockSlot < 0) return;
        mc.player.getInventory().setSelectedSlot(blockSlot);

        int radius = parseInt(getSetting("Radius"), 3);
        BlockPos playerPos = mc.player.getBlockPos();

        // Pick a random position near the player
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt(3) - 1;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos target = playerPos.add(dx, dy, dz);

            if (!mc.world.getBlockState(target).isAir()) continue;
            // Need a solid neighbor to place against
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = target.offset(dir);
                if (!mc.world.getBlockState(neighbor).isAir()) {
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(neighbor), dir.getOpposite(), neighbor, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    return;
                }
            }
        }
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}
