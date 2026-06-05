package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class NukerLegit extends Module {

    public static NukerLegit INSTANCE;

    private BlockPos currentTarget = null;
    private int miningTicks = 0;

    public NukerLegit() {
        super("NukerLegit", "Mines blocks one at a time at realistic speed", Category.WORLD);
        INSTANCE = this;
        addNumber("Radius", 4, 1, 6, 1, true);
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        miningTicks = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int radius = parseInt(getSetting("Radius"), 4);
        BlockPos playerPos = mc.player.getBlockPos();

        // Find closest block if no current target or target is gone
        if (currentTarget == null || mc.world.getBlockState(currentTarget).isAir()) {
            currentTarget = null;
            miningTicks = 0;
            double bestDist = Double.MAX_VALUE;

            for (BlockPos pos : BlockPos.iterateOutwards(playerPos, radius, radius, radius)) {
                var state = mc.world.getBlockState(pos);
                if (state.isAir()) continue;
                if (state.getHardness(mc.world, pos) < 0) continue;
                double dist = pos.getSquaredDistance(playerPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    currentTarget = pos.toImmutable();
                }
            }
        }

        if (currentTarget == null) return;

        // Attack block continuously (vanilla mining behavior)
        mc.interactionManager.attackBlock(currentTarget, Direction.UP);
        miningTicks++;

        // Estimate break time and reset target after enough ticks
        var state = mc.world.getBlockState(currentTarget);
        float hardness = state.getHardness(mc.world, currentTarget);
        int breakTicks = Math.max(1, (int)(hardness * 30));
        if (miningTicks >= breakTicks) {
            currentTarget = null;
            miningTicks = 0;
        }
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
