package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", "Always sprint (even sideways)", Category.MOVEMENT);
        addSetting("Mode", "Omni"); // Omni | Forward
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        boolean omni = "Omni".equals(getSetting("Mode"));
        var opts = client.options;
        boolean moving = opts.forwardKey.isPressed()
            || (omni && (opts.backKey.isPressed() || opts.leftKey.isPressed() || opts.rightKey.isPressed()));

        if (moving && !client.player.isTouchingWater()) {
            client.player.setSprinting(true);
        }
    }
}
