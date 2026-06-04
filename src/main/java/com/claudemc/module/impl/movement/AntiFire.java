package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * AntiFire – prevents fire damage and removes fire visual on the player.
 *
 * Requires a mixin on Entity to cancel fire ticks and block fire visual.
 * The INSTANCE flag is checked by AntiFireMixin.
 * Also extinguishes the player each tick as a client-side measure.
 */
public class AntiFire extends Module {

    public static AntiFire INSTANCE;

    public AntiFire() {
        super("AntiFire", "Prevents fire damage and fire visual", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Extinguish client-side fire each tick
        if (client.player.isOnFire()) {
            client.player.extinguish();
        }
    }
}
