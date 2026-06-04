package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * BaseFinder — highlights blocks that indicate a player-built base in the wilderness.
 * Looks for crafted/placed blocks that are not naturally generated:
 * chests, furnaces, crafting tables, beds, signs, barrels, hoppers, etc.
 */
public class BaseFinder extends BlockScanModule {

    public static BaseFinder INSTANCE;

    private static final Set<String> BASE_BLOCKS = Set.of(
        "minecraft:chest",           "minecraft:trapped_chest",
        "minecraft:ender_chest",     "minecraft:barrel",
        "minecraft:furnace",         "minecraft:blast_furnace",
        "minecraft:smoker",
        "minecraft:crafting_table",
        "minecraft:anvil",           "minecraft:chipped_anvil",   "minecraft:damaged_anvil",
        "minecraft:enchanting_table",
        "minecraft:brewing_stand",
        "minecraft:hopper",          "minecraft:dropper",         "minecraft:dispenser",
        "minecraft:beacon",
        "minecraft:bed",             "minecraft:white_bed",       "minecraft:red_bed",
        "minecraft:orange_bed",      "minecraft:yellow_bed",      "minecraft:lime_bed",
        "minecraft:green_bed",       "minecraft:cyan_bed",        "minecraft:blue_bed",
        "minecraft:purple_bed",      "minecraft:magenta_bed",     "minecraft:pink_bed",
        "minecraft:brown_bed",       "minecraft:black_bed",       "minecraft:gray_bed",
        "minecraft:light_gray_bed",  "minecraft:light_blue_bed",
        "minecraft:composter",       "minecraft:lectern",
        "minecraft:grindstone",      "minecraft:stonecutter",     "minecraft:loom",
        "minecraft:cartography_table","minecraft:fletching_table","minecraft:smithing_table"
    );

    public BaseFinder() {
        super("BaseFinder", "Highlights player base blocks (chests, furnaces, crafting tables) in the wild", Category.RENDER);
        addSetting("Radius", "48");
        INSTANCE = this;
        maxRadius = 96;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(48);
        var pPos   = client.player.getBlockPos();
        var reg    = Registries.BLOCK;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(), pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    var state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String id = reg.getId(state.getBlock()).toString();
                    if (!BASE_BLOCKS.contains(id)) continue;
                    float[] col = colorFor(id);
                    out.add(new Highlight(x, y, z, col[0], col[1], col[2]));
                }
            }
        }
    }

    private float[] colorFor(String id) {
        if (id.contains("chest"))         return new float[]{1f, 0.8f, 0f};
        if (id.contains("furnace") || id.contains("smoker") || id.contains("blast")) return new float[]{0.8f, 0.4f, 0.1f};
        if (id.contains("crafting"))      return new float[]{0.6f, 0.9f, 0.6f};
        if (id.contains("beacon"))        return new float[]{0f, 1f, 1f};
        if (id.contains("bed"))           return new float[]{1f, 0.3f, 0.5f};
        if (id.contains("enchanting"))    return new float[]{0.5f, 0f, 1f};
        return new float[]{0.9f, 0.9f, 0.9f};
    }
}
