package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Prevents fall damage by resetting fallDistance client-side.
 * Full server-side protection is handled by ClientPlayerEntityMixin
 * which sets onGround=true in movement packets when falling.
 */
public class NoFall extends Module {

    public static NoFall INSTANCE;

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.isGliding()) return;
        if (client.player.fallDistance > 2.0f) {
            client.player.fallDistance = 0;
        }
    }
}
