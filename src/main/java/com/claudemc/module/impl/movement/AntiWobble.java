package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * AntiWobble – disables screen wobble/bob effects (view bobbing when walking).
 *
 * Minecraft has a "View Bobbing" option. This module disables it when active
 * and restores it when disabled.
 *
 * Additional nausea/hurt wobble effects would require a mixin on InGameHud
 * or GameRenderer to cancel tilt/sway animations.
 */
public class AntiWobble extends Module {

    public static AntiWobble INSTANCE;

    private boolean prevViewBobbing;

    public AntiWobble() {
        super("AntiWobble", "Disables screen wobble and view bobbing", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.options != null) {
            prevViewBobbing = c.options.getBobView().getValue();
            c.options.getBobView().setValue(false);
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.options != null) {
            c.options.getBobView().setValue(prevViewBobbing);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Keep view bobbing disabled while module is active
        if (client.options != null && client.options.getBobView().getValue()) {
            client.options.getBobView().setValue(false);
        }
    }
}
