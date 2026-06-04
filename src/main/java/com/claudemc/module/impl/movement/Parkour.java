package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Parkour – automatically jumps at block edges to prevent falling off.
 */
public class Parkour extends Module {

    public static Parkour INSTANCE;

    public Parkour() {
        super("Parkour", "Auto-jump at block edges to avoid falling", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!client.player.isOnGround()) return;
        if (client.player.isSneaking()) return;
        if (client.player.isJumping) return;

        var opts = client.options;
        boolean moving = opts.forwardKey.isPressed() || opts.backKey.isPressed()
                      || opts.leftKey.isPressed()    || opts.rightKey.isPressed();
        if (!moving) return;

        // Check if there's no block below the player in the direction of movement
        // by checking the block one step ahead and one below
        var pos = client.player.getBlockPos();
        // Check the block one unit below current position
        BlockPos below = pos.down();

        if (client.world.isAir(below)) {
            // Player is at edge – jump
            client.player.jump();
        }
    }
}
