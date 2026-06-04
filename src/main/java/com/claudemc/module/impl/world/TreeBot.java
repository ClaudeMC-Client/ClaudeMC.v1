package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

public class TreeBot extends Module {

    public static TreeBot INSTANCE;

    private final Queue<BlockPos> logQueue = new ArrayDeque<>();
    private boolean scanning = true;

    public TreeBot() {
        super("TreeBot", "Cuts down entire trees by breaking all connected logs", Category.WORLD);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        logQueue.clear();
        scanning = true;
    }

    @Override
    public void onDisable() {
        logQueue.clear();
        scanning = true;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (scanning) {
            // Find a log near the player
            BlockPos playerPos = mc.player.getBlockPos();
            BlockPos startLog = null;
            for (BlockPos pos : BlockPos.iterateOutwards(playerPos, 4, 4, 4)) {
                if (isLog(mc.world.getBlockState(pos).getBlock())) {
                    startLog = pos.toImmutable();
                    break;
                }
            }
            if (startLog == null) return;

            // BFS to find all connected logs
            Set<BlockPos> visited = new HashSet<>();
            Queue<BlockPos> bfsQueue = new ArrayDeque<>();
            bfsQueue.add(startLog);
            visited.add(startLog);

            while (!bfsQueue.isEmpty()) {
                BlockPos current = bfsQueue.poll();
                logQueue.add(current);
                // Check 6 neighbors
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.offset(dir);
                    if (!visited.contains(neighbor) && isLog(mc.world.getBlockState(neighbor).getBlock())) {
                        visited.add(neighbor);
                        bfsQueue.add(neighbor);
                    }
                }
            }
            scanning = false;
        }

        // Mine next log in queue
        while (!logQueue.isEmpty()) {
            BlockPos pos = logQueue.peek();
            var state = mc.world.getBlockState(pos);
            if (state.isAir() || !isLog(state.getBlock())) {
                logQueue.poll();
                continue;
            }
            mc.interactionManager.attackBlock(pos, Direction.DOWN);
            return;
        }

        // Queue empty - done, re-scan next tick
        scanning = true;
    }

    private boolean isLog(Block block) {
        return block == Blocks.OAK_LOG || block == Blocks.BIRCH_LOG || block == Blocks.SPRUCE_LOG
            || block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG
            || block == Blocks.MANGROVE_LOG || block == Blocks.CHERRY_LOG || block == Blocks.BAMBOO_BLOCK
            || block == Blocks.STRIPPED_OAK_LOG || block == Blocks.STRIPPED_BIRCH_LOG
            || block == Blocks.STRIPPED_SPRUCE_LOG || block == Blocks.STRIPPED_JUNGLE_LOG
            || block == Blocks.STRIPPED_ACACIA_LOG || block == Blocks.STRIPPED_DARK_OAK_LOG;
    }
}
