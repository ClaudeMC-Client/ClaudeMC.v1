package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Highlights specific blocks by registry name.
 * Set the "Block" setting to a block id such as {@code minecraft:chest} or just {@code chest}.
 */
public class Search extends BlockScanModule {

    public static Search INSTANCE;

    public Search() {
        super("Search", "Highlights specific blocks by name in a radius", Category.RENDER);
        addSetting("Block", "minecraft:chest");
        addSetting("Radius", "24");
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        String blockName = getSetting("Block").trim().toLowerCase();
        if (blockName.isEmpty()) return;

        // Accept shorthand like "chest" → "minecraft:chest"
        if (!blockName.contains(":")) blockName = "minecraft:" + blockName;

        var targetId = Identifier.tryParse(blockName);
        if (targetId == null) return;
        if (!Registries.BLOCK.containsId(targetId)) return;
        var target = Registries.BLOCK.get(targetId);

        int radius = radius(24);
        var pPos   = client.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(), pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (client.world.getBlockState(pos).getBlock() == target) {
                        out.add(new Highlight(x, y, z, 1f, 0.5f, 0f));
                    }
                }
            }
        }
    }
}
