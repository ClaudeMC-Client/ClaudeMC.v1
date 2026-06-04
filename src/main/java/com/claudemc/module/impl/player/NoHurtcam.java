package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Disables the red damage screen shake when taking damage.
 * The actual cancellation is applied in NoHurtcamMixin.
 */
public class NoHurtcam extends Module {

    public static NoHurtcam INSTANCE;

    public NoHurtcam() {
        super("NoHurtcam", "Disables red damage screen shake", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
