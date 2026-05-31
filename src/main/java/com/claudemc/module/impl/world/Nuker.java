package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Nuker extends Module {

    public Nuker() {
        super("Nuker", "Automatically breaks blocks around you", Category.WORLD);
        addNumber("Radius", 4, 1, 16, 1, true);
        addMode("Mode", "All", "All", "Flat", "Above");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.interactionManager == null) return;

        int radius = parseInt(getSetting("Radius"), 4);
        String mode = getSetting("Mode");
        var pPos = client.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterateOutwards(pPos, radius, radius, radius)) {
            var state = client.world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getHardness(client.world, pos) < 0) continue; // unbreakable
            if ("Flat".equals(mode) && pos.getY() != pPos.getY()) continue;
            if ("Above".equals(mode) && pos.getY() <= pPos.getY()) continue;

            client.interactionManager.attackBlock(pos, Direction.UP);
            return; // one block per tick
        }
    }

    private int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }
}
