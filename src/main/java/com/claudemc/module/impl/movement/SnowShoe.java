package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;

/**
 * SnowShoe – walk on powder snow without sinking by counteracting the sinking velocity.
 *
 * Full prevention requires a mixin on Entity.move() or PowderSnowBlock to skip
 * the sinking logic. This module counters the downward velocity applied by powder snow.
 * Provides INSTANCE for a future mixin.
 */
public class SnowShoe extends Module {

    public static SnowShoe INSTANCE;

    public SnowShoe() {
        super("SnowShoe", "Walk on powder snow without sinking", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        var pos = client.player.getBlockPos();
        var state = client.world.getBlockState(pos);

        if (state.isOf(Blocks.POWDER_SNOW)) {
            var vel = client.player.getVelocity();
            // Counteract the sinking: keep y velocity at 0 or positive
            if (vel.y < 0) {
                client.player.setVelocity(vel.x, 0, vel.z);
            }
        }
    }
}
