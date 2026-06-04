package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Automatically swims upward when the player is submerged in fluid.
 */
public class AutoSwim extends Module {

    public static AutoSwim INSTANCE;

    public AutoSwim() {
        super("AutoSwim", "Automatically swims upward in water", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.isTouchingWater() || client.player.isInLava()) {
            client.player.setVelocity(
                client.player.getVelocity().x,
                Math.max(client.player.getVelocity().y, 0.12),
                client.player.getVelocity().z
            );
            client.player.jumping = true;
        }
    }
}
