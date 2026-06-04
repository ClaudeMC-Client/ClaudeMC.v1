package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Highlights newly generated chunks. A chunk is considered "new" if it contains
 * no bedrock at the world bottom (indicating it was freshly generated after the
 * world was explored — newly generated chunks may have a different bedrock pattern).
 *
 * Detection strategy: on each tick scan loaded chunks around the player. A chunk
 * is marked "new" if the bedrock layer (bottom Y) contains fewer than expected
 * bedrock blocks. In practice we check a sample of positions; if bedrock count < 4
 * out of 16 sampled positions, the chunk was recently generated (new terrain).
 */
public class NewChunks extends Module {

    public static NewChunks INSTANCE;

    private volatile Set<ChunkPos> newChunks    = Collections.synchronizedSet(new HashSet<>());
    private volatile Set<ChunkPos> checkedChunks = Collections.synchronizedSet(new HashSet<>());
    private int cooldown = 0;

    public NewChunks() {
        super("NewChunks", "Highlights newly generated chunks", Category.RENDER);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam      = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();

            int playerY = client.player.getBlockY();
            for (ChunkPos cp : newChunks) {
                double wx = cp.getStartX() - cam.x;
                double wz = cp.getStartZ() - cam.z;
                double wy = playerY - cam.y;
                Box box = new Box(wx, wy, wz, wx + 16, wy + 1, wz + 16);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, 0f, 1f, 1f, 1f);
            }
        });
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (--cooldown > 0) return;
        cooldown = 20;

        int chunkX = client.player.getChunkPos().x;
        int chunkZ = client.player.getChunkPos().z;
        int bottomY = client.world.getBottomY();

        for (int cx = chunkX - 8; cx <= chunkX + 8; cx++) {
            for (int cz = chunkZ - 8; cz <= chunkZ + 8; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (checkedChunks.contains(cp)) continue;
                if (!client.world.isChunkLoaded(cx, cz)) continue;

                checkedChunks.add(cp);
                int bedrockCount = 0;
                BlockPos.Mutable pos = new BlockPos.Mutable();
                // Sample 16 positions across the chunk base
                for (int lx = 0; lx < 16; lx += 4) {
                    for (int lz = 0; lz < 16; lz += 4) {
                        pos.set(cp.getStartX() + lx, bottomY, cp.getStartZ() + lz);
                        if (client.world.getBlockState(pos).getBlock() == Blocks.BEDROCK) {
                            bedrockCount++;
                        }
                    }
                }
                // New chunks in 1.17+ have bedrock only at bottomY; old chunks have a thicker layer
                // Newly generated chunks often have no bedrock at all (below deep dark layer)
                // or a very sparse pattern — flag if < 4 bedrock blocks sampled
                if (bedrockCount < 4) {
                    newChunks.add(cp);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        newChunks.clear();
        checkedChunks.clear();
    }
}
