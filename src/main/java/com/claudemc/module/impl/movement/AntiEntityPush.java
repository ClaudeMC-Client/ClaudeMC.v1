package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * AntiEntityPush – prevents entities from pushing the player.
 *
 * Requires a mixin on Entity.pushAwayFrom() to cancel entity collision pushback.
 * The INSTANCE flag is checked by AntiEntityPushMixin.
 */
public class AntiEntityPush extends Module {

    public static AntiEntityPush INSTANCE;

    public AntiEntityPush() {
        super("AntiEntityPush", "Prevents entities from pushing the player", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Logic handled in AntiEntityPushMixin
    }
}
