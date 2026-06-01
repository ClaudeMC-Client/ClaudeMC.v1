package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

public class OreESP extends BlockScanModule {

    private static final Set<String> ORES = Set.of(
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
        "minecraft:nether_quartz_ore"
    );

    public OreESP() {
        super("OreESP", "Highlights ores through walls (X-ray scanner)", Category.RENDER);
        addSetting("Radius", "16");
        addMode("Tier", "All", "All", "Valuable", "Diamond+", "AncientDebris");
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius    = radius(16);
        var pPos      = client.player.getBlockPos();
        var reg       = Registries.BLOCK;
        String tier   = getSetting("Tier");

        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(client.world.getBottomY(), pPos.getY() - radius);
        int maxY = Math.min(client.world.getTopYInclusive(),    pPos.getY() + radius);

        for (int x = pPos.getX() - radius; x <= pPos.getX() + radius; x++) {
            for (int z = pPos.getZ() - radius; z <= pPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    var state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String id = reg.getId(state.getBlock()).toString();
                    if (!ORES.contains(id)) continue;
                    if (!matchesTier(id, tier)) continue;
                    float[] col = colorForOre(id);
                    out.add(new Highlight(x, y, z, col[0], col[1], col[2]));
                }
            }
        }
    }

    private boolean matchesTier(String id, String tier) {
        return switch (tier) {
            case "Valuable"    -> id.contains("diamond") || id.contains("emerald")
                                  || id.contains("ancient_debris") || id.contains("gold");
            case "Diamond+"    -> id.contains("diamond") || id.contains("emerald")
                                  || id.contains("ancient_debris");
            case "AncientDebris" -> id.contains("ancient_debris");
            default            -> true;
        };
    }

    private float[] colorForOre(String id) {
        if (id.contains("diamond"))       return new float[]{0.0f, 0.8f, 1.0f};
        if (id.contains("emerald"))       return new float[]{0.0f, 1.0f, 0.2f};
        if (id.contains("ancient"))       return new float[]{0.9f, 0.5f, 0.1f};
        if (id.contains("gold"))          return new float[]{1.0f, 0.9f, 0.0f};
        if (id.contains("iron"))          return new float[]{0.9f, 0.8f, 0.7f};
        if (id.contains("lapis"))         return new float[]{0.2f, 0.3f, 1.0f};
        if (id.contains("redstone"))      return new float[]{1.0f, 0.1f, 0.1f};
        if (id.contains("copper"))        return new float[]{0.9f, 0.5f, 0.3f};
        return new float[]{0.7f, 0.7f, 0.7f};
    }
}
