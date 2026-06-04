package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Removes the semi-transparent background behind GUI screens.
 * Cancelled via NoBackgroundMixin (injects into Screen.renderBackground).
 */
public class NoBackground extends Module {

    public static NoBackground INSTANCE;

    public NoBackground() {
        super("NoBackground", "Removes GUI background overlay", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
