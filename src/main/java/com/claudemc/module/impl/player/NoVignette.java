package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Removes the dark vignette overlay (corners of the screen).
 * Cancelled via NoVignetteMixin.
 */
public class NoVignette extends Module {

    public static NoVignette INSTANCE;

    public NoVignette() {
        super("NoVignette", "Removes vignette (dark corners)", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
