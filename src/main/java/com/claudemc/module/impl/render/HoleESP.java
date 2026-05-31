package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Highlights 1x1 holes safe from crystal explosions (surrounded by bedrock/obsidian).
 */
public class HoleESP extends BlockScanModule {

    public HoleESP() {
        super("HoleESP", "Highlights safe holes (bedrock/obsidian-surrounded) around you", Category.RENDER);
        addSetting("Radius", "16");
        addBool("BedrockOnly", false);
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(16);
        var playerPos = client.player.getBlockPos();
        boolean bedrockOnly = Boolean.parseBoolean(getSetting("BedrockOnly"));

        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                for (int y = client.world.getBottomY(); y < client.world.getTopY() - 2; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!client.world.getBlockState(pos).isAir()) continue;
                    if (!client.world.getBlockState(pos.up()).isAir()) continue;
                    if (!isSafe(client, pos.down(), bedrockOnly)) continue;
                    if (!isSafe(client, pos.north(), bedrockOnly)) continue;
                    if (!isSafe(client, pos.south(), bedrockOnly)) continue;
                    if (!isSafe(client, pos.east(), bedrockOnly)) continue;
                    if (!isSafe(client, pos.west(), bedrockOnly)) continue;

                    boolean bedrock = client.world.getBlockState(pos.down()).getBlock() == Blocks.BEDROCK;
                    if (bedrock) out.add(new Highlight(x, y, z, 0.2f, 0.8f, 1.0f));
                    else         out.add(new Highlight(x, y, z, 0.6f, 0.6f, 1.0f));
                }
            }
        }
    }

    private boolean isSafe(MinecraftClient client, BlockPos pos, boolean bedrockOnly) {
        var block = client.world.getBlockState(pos).getBlock();
        if (bedrockOnly) return block == Blocks.BEDROCK;
        return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN
            || block == Blocks.CRYING_OBSIDIAN || block == Blocks.NETHERITE_BLOCK;
    }
}
