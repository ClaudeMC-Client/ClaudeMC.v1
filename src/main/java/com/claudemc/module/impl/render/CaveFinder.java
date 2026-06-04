package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Highlights underground air pockets (caves) below the surface.
 * A block is considered a "cave air" position when:
 *   - It is air or a fluid
 *   - It is below y=60 (configurable)
 *   - At least one of the 6 neighbouring blocks is solid
 * Only the boundary (exposed surface of the air pocket) is highlighted so the
 * outline remains visible and distinct rather than filling the entire void.
 */
public class CaveFinder extends BlockScanModule {

    public static CaveFinder INSTANCE;

    public CaveFinder() {
        super("CaveFinder", "Highlights cave air pockets underground", Category.RENDER);
        addSetting("Radius", "24");
        addSetting("MaxY", "60");
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(24);
        int maxY   = parseInt(getSetting("MaxY"), 60);
        var pPos   = client.player.getBlockPos();
        BlockPos.Mutable pos    = new BlockPos.Mutable();
        BlockPos.Mutable neigh = new BlockPos.Mutable();

        int yFrom = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int yTo   = Math.min(maxY, pPos.getY() + radius);

        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0,  0, 1,-1, 0, 0};
        int[] dz = {0,  0, 0, 0, 1,-1};

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = yFrom; y <= yTo; y++) {
                    pos.set(x, y, z);
                    var state = client.world.getBlockState(pos);
                    if (!state.isAir() && !state.isLiquid()) continue;
                    // Check for at least one solid neighbour
                    boolean hasSolid = false;
                    for (int i = 0; i < 6; i++) {
                        neigh.set(x + dx[i], y + dy[i], z + dz[i]);
                        var ns = client.world.getBlockState(neigh);
                        if (!ns.isAir() && !ns.isLiquid()) { hasSolid = true; break; }
                    }
                    if (hasSolid) {
                        out.add(new Highlight(x, y, z, 0.3f, 0.7f, 1f));
                    }
                }
            }
        }
    }
}
