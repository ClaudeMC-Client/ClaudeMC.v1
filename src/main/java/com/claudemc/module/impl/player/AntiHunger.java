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
        // Always prevent sprint-exhaustion — same approach as Meteor's AntiHunger
        if (client.player.isSprinting()) {
            client.player.setSprinting(false);
        }
    }
}
