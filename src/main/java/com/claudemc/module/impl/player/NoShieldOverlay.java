package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Removes the shield overlay visual on the HUD.
 * Cancelled via NoShieldOverlayMixin.
 */
public class NoShieldOverlay extends Module {

    public static NoShieldOverlay INSTANCE;

    public NoShieldOverlay() {
        super("NoShieldOverlay", "Removes shield overlay visual", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
