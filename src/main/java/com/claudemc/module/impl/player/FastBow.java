package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Instant bow charge — arrows are fired at full power immediately.
 * FastBowMixin overrides the bow's charge query to return max charge.
 */
public class FastBow extends Module {

    public static FastBow INSTANCE;

    public FastBow() {
        super("FastBow", "Instant bow charge", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
