package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {

    private int delay = 0;

    public Scaffold() {
        super("Scaffold", "Automatically places blocks beneath you", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (delay-- > 0) return;

        BlockPos below = client.player.getBlockPos().down();
        BlockState state = client.world.getBlockState(below);
        if (!state.isAir()) return;

        int slot = findBlock(client);
        if (slot < 0) return;

        int prevSlot = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(slot);

        Vec3d placePos = new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
        var hit = new BlockHitResult(placePos, Direction.UP, below, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.getInventory().setSelectedSlot(prevSlot);
        delay = 2;
    }

    private int findBlock(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof BlockItem bi) {
                if (bi.getBlock().getDefaultState().isSolidBlock(
                        client.world, client.player.getBlockPos().down())) {
                    return i;
                }
            }
        }
        return -1;
    }
}
