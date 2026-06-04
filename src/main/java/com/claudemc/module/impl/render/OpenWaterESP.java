package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Highlights open water areas — water blocks that have sky access above them
 * (i.e. the topmost water block in a column).
 */
public class OpenWaterESP extends BlockScanModule {

    public static OpenWaterESP INSTANCE;

    public OpenWaterESP() {
        super("OpenWaterESP", "Highlights open water surface areas", Category.RENDER);
        addSetting("Radius", "32");
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(32);
        var pPos   = client.player.getBlockPos();
        BlockPos.Mutable pos   = new BlockPos.Mutable();
        BlockPos.Mutable above = new BlockPos.Mutable();

        int minY = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(), pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = maxY; y >= minY; y--) {
                    pos.set(x, y, z);
                    var state = client.world.getBlockState(pos);
                    if (state.getBlock() != Blocks.WATER) continue;

                    // Check that the block above is air (surface water)
                    above.set(x, y + 1, z);
                    var aboveState = client.world.getBlockState(above);
                    if (!aboveState.isAir()) continue;

                    out.add(new Highlight(x, y, z, 0f, 0.5f, 1f));
                    break; // only highlight topmost water in column
                }
            }
        }
    }
}
