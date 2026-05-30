package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class SafeWalk extends Module {

    public SafeWalk() {
        super("SafeWalk", "Prevents walking off edges (like sneaking)", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Temporarily enable sneak collision without actually sneaking
        client.player.setSneaking(true);
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null && !c.options.sneakKey.isPressed()) {
            c.player.setSneaking(false);
        }
    }
}
