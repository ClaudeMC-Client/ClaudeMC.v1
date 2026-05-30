package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Reduces hunger depletion by preventing sprinting (which costs extra hunger).
 * Full server-side prevention requires packet modification.
 */
public class AntiHunger extends Module {

    public AntiHunger() {
        super("AntiHunger", "Slows hunger depletion by avoiding sprint exhaustion", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Prevent sprint-exhaustion by toggling sprint off when not strictly needed
        if (client.player.isSprinting() && client.player.getHungerManager().getFoodLevel() <= 6) {
            client.player.setSprinting(false);
        }
    }
}
