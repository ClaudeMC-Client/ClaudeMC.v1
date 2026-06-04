package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class PortalESP extends BlockScanModule {

    public static PortalESP INSTANCE;

    public PortalESP() {
        super("PortalESP", "Highlights Nether and End portal blocks through walls", Category.RENDER);
        addSetting("Radius", "48");
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(48);
        var pPos   = client.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(), pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    var block = client.world.getBlockState(pos).getBlock();
                    if (block == Blocks.NETHER_PORTAL) {
                        out.add(new Highlight(x, y, z, 0.7f, 0f, 1f));
                    } else if (block == Blocks.END_PORTAL) {
                        out.add(new Highlight(x, y, z, 0f, 0.8f, 0.4f));
                    } else if (block == Blocks.END_GATEWAY) {
                        out.add(new Highlight(x, y, z, 0f, 0.5f, 1f));
                    }
                }
            }
        }
    }
}
