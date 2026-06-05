package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

public class VeinMiner extends Module {

    private int delay = 0;

    public VeinMiner() {
        super("VeinMiner", "Mines entire ore veins at once when you break one block", Category.WORLD);
        addSetting("MaxBlocks", "32");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (delay-- > 0) return;

        var crosshair = client.crosshairTarget;
        if (!(crosshair instanceof net.minecraft.util.hit.BlockHitResult bhr)) return;
        if (!client.options.attackKey.isPressed()) return;

        BlockPos origin = bhr.getBlockPos();
        Block target = client.world.getBlockState(origin).getBlock();
        if (target == net.minecraft.block.Blocks.AIR) return;

        int max = parseInt(getSetting("MaxBlocks"), 32);
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(origin);

        while (!queue.isEmpty() && visited.size() < max) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) continue;
            if (client.world.getBlockState(pos).getBlock() != target) continue;

            client.interactionManager.attackBlock(pos, Direction.UP);
            for (Direction d : Direction.values()) queue.add(pos.offset(d));
        }
        delay = 5;
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
