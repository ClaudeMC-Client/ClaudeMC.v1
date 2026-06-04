package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.List;

/**
 * Highlights blocks where hostile mobs can spawn:
 *  - The block itself is air (spawn position)
 *  - The block directly below has a solid top surface
 *  - Block light level == 0 (in 1.17+ mobs need 0 block light to spawn)
 */
public class MobSpawnESP extends BlockScanModule {

    public static MobSpawnESP INSTANCE;

    public MobSpawnESP() {
        super("MobSpawnESP", "Highlights blocks where mobs can spawn (dark surfaces)", Category.RENDER);
        addSetting("Radius", "20");
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(20);
        var pPos   = client.player.getBlockPos();
        BlockPos.Mutable pos   = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();

        int minY = Math.max(client.world.getBottomY() + 1, pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(), pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (!client.world.getBlockState(pos).isAir()) continue;

                    below.set(x, y - 1, z);
                    BlockState belowState = client.world.getBlockState(below);
                    if (belowState.isAir() || belowState.isLiquid()) continue;
                    if (!belowState.isSolidBlock(client.world, below)) continue;

                    int blockLight = client.world.getLightLevel(LightType.BLOCK, pos);
                    if (blockLight > 0) continue;

                    int skyLight = client.world.getLightLevel(LightType.SKY, pos);
                    // Red = always spawnable, orange = only at night
                    if (skyLight == 0) {
                        out.add(new Highlight(x, y, z, 1f, 0f, 0f));
                    } else {
                        out.add(new Highlight(x, y, z, 1f, 0.5f, 0f));
                    }
                }
            }
        }
    }
}
