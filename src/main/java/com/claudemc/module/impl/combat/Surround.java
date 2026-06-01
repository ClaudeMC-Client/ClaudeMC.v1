package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Surround extends Module {

    private int placeIndex = 0;

    public Surround() {
        super("Surround", "Places obsidian around your feet to protect against crystals", Category.COMBAT);
        addMode("Material", "Obsidian", "Obsidian", "Any");
        addBool("Center", true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        if (Boolean.parseBoolean(getSetting("Center"))) {
            BlockPos feet = client.player.getBlockPos();
            Vec3d center = Vec3d.ofCenter(feet);
            if (client.player.getX() != center.x || client.player.getZ() != center.z) {
                client.player.setVelocity(0, client.player.getVelocity().y, 0);
            }
        }

        List<BlockPos> surroundPositions = getSurroundPositions(client);
        if (surroundPositions.isEmpty()) return;

        if (placeIndex >= surroundPositions.size()) placeIndex = 0;
        BlockPos target = surroundPositions.get(placeIndex);
        placeIndex++;

        int slot = findSuitableBlock(client);
        if (slot == -1) return;

        int prev = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(slot);

        // Place against the block below
        BlockPos below = target.down();
        if (!client.world.getBlockState(below).isAir()) {
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(below).add(0, 0.5, 0), Direction.UP, below, false));
        }

        client.player.getInventory().setSelectedSlot(prev);
    }

    private List<BlockPos> getSurroundPositions(MinecraftClient client) {
        List<BlockPos> needed = new ArrayList<>();
        BlockPos feet = client.player.getBlockPos();
        int[] xOffsets = {0,  0,  1, -1};
        int[] zOffsets = {1, -1,  0,  0};
        for (int i = 0; i < 4; i++) {
            BlockPos p = feet.add(xOffsets[i], 0, zOffsets[i]);
            if (client.world.getBlockState(p).isAir()) needed.add(p);
        }
        return needed;
    }

    private int findSuitableBlock(MinecraftClient client) {
        var inv = client.player.getInventory();
        boolean obsOnly = "Obsidian".equals(getSetting("Material"));
        for (int i = 0; i < 9; i++) {
            var stack = inv.getStack(i);
            if (!(stack.getItem() instanceof BlockItem)) continue;
            if (obsOnly && stack.getItem() != Items.OBSIDIAN) continue;
            return i;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        placeIndex = 0;
    }
}
