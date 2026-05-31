package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Removes the 4-tick block placement cooldown.
 * The cooldown reset is applied in FastPlaceMixin targeting
 * ClientPlayerInteractionManager.interactBlock.
 */
public class FastPlace extends Module {

    public static FastPlace INSTANCE;

    public FastPlace() {
        super("FastPlace", "Removes the block placement delay for instant placement", Category.PLAYER);
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}
}
