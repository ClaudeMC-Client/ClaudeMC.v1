package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class BarrierESP extends BlockScanModule {

    public static BarrierESP INSTANCE;

    public BarrierESP() {
        super("BarrierESP", "Highlights invisible barrier blocks", Category.RENDER);
        addSetting("Radius", "32");
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(32);
        var pPos   = client.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(), pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (client.world.getBlockState(pos).getBlock() == Blocks.BARRIER) {
                        out.add(new Highlight(x, y, z, 1f, 0f, 0f));
                    }
                }
            }
        }
    }
}
