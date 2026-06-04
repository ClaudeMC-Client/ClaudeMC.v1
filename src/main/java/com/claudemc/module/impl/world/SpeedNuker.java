package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class SpeedNuker extends Module {

    public static SpeedNuker INSTANCE;

    public SpeedNuker() {
        super("SpeedNuker", "Mines blocks in a sphere at very high speed", Category.WORLD);
        INSTANCE = this;
        addNumber("Radius", 5, 1, 10, 1, true);
        addNumber("BlocksPerTick", 10, 1, 50, 1, true);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int radius = parseInt(getSetting("Radius"), 5);
        int blocksPerTick = parseInt(getSetting("BlocksPerTick"), 10);
        BlockPos playerPos = mc.player.getBlockPos();
        int broken = 0;

        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, radius, radius, radius)) {
            if (broken >= blocksPerTick) break;
            var state = mc.world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getHardness(mc.world, pos) < 0) continue;

            double dist = Math.sqrt(pos.getSquaredDistance(playerPos));
            if (dist > radius) continue;

            mc.interactionManager.attackBlock(pos, Direction.UP);
            broken++;
        }
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}
