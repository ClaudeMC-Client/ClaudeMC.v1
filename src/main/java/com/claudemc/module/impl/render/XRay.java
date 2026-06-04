package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * Extended X-Ray ESP — highlights ores and valuable blocks at an extended range.
 * This is a client-side overlay approach (not chunk-mesh replacement).
 * It highlights ALL ore types within a larger radius so players can locate them
 * without having to look through walls manually.
 */
public class XRay extends BlockScanModule {

    public static XRay INSTANCE;

    private static final Set<String> XRAY_BLOCKS = Set.of(
        // Ores
        "minecraft:diamond_ore",         "minecraft:deepslate_diamond_ore",
        "minecraft:emerald_ore",         "minecraft:deepslate_emerald_ore",
        "minecraft:ancient_debris",
        "minecraft:gold_ore",            "minecraft:deepslate_gold_ore",
                                         "minecraft:nether_gold_ore",
        "minecraft:iron_ore",            "minecraft:deepslate_iron_ore",
        "minecraft:copper_ore",          "minecraft:deepslate_copper_ore",
        "minecraft:lapis_ore",           "minecraft:deepslate_lapis_ore",
        "minecraft:redstone_ore",        "minecraft:deepslate_redstone_ore",
        "minecraft:coal_ore",            "minecraft:deepslate_coal_ore",
        "minecraft:nether_quartz_ore",
        // Containers / structures
        "minecraft:chest",               "minecraft:trapped_chest",
        "minecraft:ender_chest",
        "minecraft:spawner",
        "minecraft:nether_portal",       "minecraft:end_portal",
        "minecraft:obsidian"
    );

    public XRay() {
        super("XRay", "Highlights ores and valuable blocks at extended range", Category.RENDER);
        addSetting("Radius", "32");
        addMode("Mode", "Ores+", "Ores+", "OresOnly", "Containers");
        INSTANCE = this;
        maxRadius = 96; // allow up to 96 for xray
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius  = radius(32);
        String mode = getSetting("Mode");
        var pPos    = client.player.getBlockPos();
        var reg     = Registries.BLOCK;
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
                    if (!XRAY_BLOCKS.contains(id)) continue;
                    if (!matchesMode(id, mode)) continue;
                    float[] col = colorFor(id);
                    out.add(new Highlight(x, y, z, col[0], col[1], col[2]));
                }
            }
        }
    }

    private boolean matchesMode(String id, String mode) {
        boolean isOre = id.contains("_ore") || id.contains("ancient_debris");
        boolean isContainer = id.contains("chest") || id.contains("spawner");
        return switch (mode) {
            case "OresOnly"    -> isOre;
            case "Containers"  -> isContainer;
            default            -> true; // "Ores+"
        };
    }

    private float[] colorFor(String id) {
        if (id.contains("diamond"))    return new float[]{0.0f, 0.8f, 1.0f};
        if (id.contains("emerald"))    return new float[]{0.0f, 1.0f, 0.2f};
        if (id.contains("ancient"))    return new float[]{0.9f, 0.5f, 0.1f};
        if (id.contains("gold"))       return new float[]{1.0f, 0.9f, 0.0f};
        if (id.contains("iron"))       return new float[]{0.9f, 0.8f, 0.7f};
        if (id.contains("lapis"))      return new float[]{0.2f, 0.3f, 1.0f};
        if (id.contains("redstone"))   return new float[]{1.0f, 0.1f, 0.1f};
        if (id.contains("copper"))     return new float[]{0.9f, 0.5f, 0.3f};
        if (id.contains("chest"))      return new float[]{1.0f, 0.8f, 0.0f};
        if (id.contains("spawner"))    return new float[]{0.5f, 0.0f, 0.5f};
        if (id.contains("portal"))     return new float[]{0.7f, 0.0f, 1.0f};
        if (id.contains("obsidian"))   return new float[]{0.3f, 0.0f, 0.5f};
        return new float[]{0.7f, 0.7f, 0.7f};
    }
}
