package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a simple floor or platform of blocks in a radius around the player.
 */
public class AutoBuild extends Module {

    public AutoBuild() {
        super("AutoBuild", "Auto-places blocks to build a floor/bridge under the player", Category.WORLD);
        addMode("Shape",  "Floor", "Floor", "Bridge", "Column");
        addNumber("Radius", 3, 1, 8, 1, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        int slot = findBlockSlot(client);
        if (slot == -1) return;
        client.player.getInventory().setSelectedSlot(slot);

        int radius = parseInt(getSetting("Radius"), 3);
        String shape = getSetting("Shape");
        var pPos = client.player.getBlockPos();

        List<BlockPos> targets = getTargetPositions(pPos, radius, shape);
        for (BlockPos pos : targets) {
            if (!client.world.getBlockState(pos).isAir()) continue;
            BlockPos below = pos.down();
            if (client.world.getBlockState(below).isAir()) continue;
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(below).add(0, 0.5, 0), Direction.UP, below, false));
            return;
        }
    }

    private List<BlockPos> getTargetPositions(BlockPos pPos, int radius, String shape) {
        List<BlockPos> list = new ArrayList<>();
        switch (shape) {
            case "Bridge" -> {
                // Place blocks directly ahead
                var client = MinecraftClient.getInstance();
                if (client.player == null) break;
                var look = client.player.getRotationVec(1.0f);
                int steps = radius + 2;
                for (int i = 1; i <= steps; i++) {
                    int bx = pPos.getX() + (int)(look.x * i);
                    int bz = pPos.getZ() + (int)(look.z * i);
                    list.add(new BlockPos(bx, pPos.getY() - 1, bz));
                }
            }
            case "Column" -> {
                for (int y = 1; y <= radius * 2; y++) list.add(pPos.up(y));
            }
            default -> { // Floor
                for (BlockPos pos : BlockPos.iterateOutwards(pPos, radius, 0, radius)) {
                    list.add(pos.down());
                }
            }
        }
        return list;
    }

    private int findBlockSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
