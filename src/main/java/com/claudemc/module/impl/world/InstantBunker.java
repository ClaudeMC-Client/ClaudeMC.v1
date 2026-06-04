package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class InstantBunker extends Module {

    public static InstantBunker INSTANCE;

    public InstantBunker() {
        super("InstantBunker", "Instantly creates a 3x3 bunker around the player", Category.WORLD);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }

        // Find block item in hotbar
        int blockSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                blockSlot = i;
                break;
            }
        }
        if (blockSlot < 0) {
            mc.player.sendMessage(Text.literal("§cInstantBunker: No blocks in hotbar!"), false);
            setEnabled(false);
            return;
        }
        mc.player.getInventory().selectedSlot = blockSlot;

        BlockPos center = mc.player.getBlockPos();

        // Build 3x3x3 shell (walls + floor + ceiling), skip player space
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    // Only shell
                    if (x != -1 && x != 1 && z != -1 && z != 1 && y != 0 && y != 2) continue;
                    BlockPos pos = center.add(x, y, z);
                    // Don't place inside player
                    if (pos.equals(center) || pos.equals(center.up())) continue;
                    if (!mc.world.getBlockState(pos).isAir()) continue;

                    // Find neighbor to place against
                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = pos.offset(dir);
                        if (!mc.world.getBlockState(neighbor).isAir()) {
                            BlockHitResult hit = new BlockHitResult(
                                Vec3d.ofCenter(neighbor), dir.getOpposite(), neighbor, false);
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                            break;
                        }
                    }
                }
            }
        }

        mc.player.sendMessage(Text.literal("§aBunker built!"), false);
        setEnabled(false);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        // All work done in onEnable
    }
}
