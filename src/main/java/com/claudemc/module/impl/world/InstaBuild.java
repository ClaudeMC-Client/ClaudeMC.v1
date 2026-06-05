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

public class InstaBuild extends Module {

    public static InstaBuild INSTANCE;

    public InstaBuild() {
        super("InstaBuild", "Places multiple blocks per tick at max speed", Category.WORLD);
        INSTANCE = this;
        addNumber("BlocksPerTick", 5, 1, 20, 1, true);
        addNumber("Radius", 4, 1, 8, 1, true);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int blocksPerTick = parseInt(getSetting("BlocksPerTick"), 5);
        int radius = parseInt(getSetting("Radius"), 4);

        // Find block item in hotbar
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

        BlockPos playerPos = mc.player.getBlockPos();
        int placed = 0;

        outer:
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (placed >= blocksPerTick) break outer;
                    BlockPos target = playerPos.add(x, y, z);
                    if (!mc.world.getBlockState(target).isAir()) continue;
                    // Ensure we're not placing inside the player
                    if (target.equals(playerPos) || target.equals(playerPos.up())) continue;

                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = target.offset(dir);
                        if (!mc.world.getBlockState(neighbor).isAir()) {
                            BlockHitResult hit = new BlockHitResult(
                                Vec3d.ofCenter(neighbor), dir.getOpposite(), neighbor, false);
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                            placed++;
                            break;
                        }
                    }
                }
            }
        }
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
