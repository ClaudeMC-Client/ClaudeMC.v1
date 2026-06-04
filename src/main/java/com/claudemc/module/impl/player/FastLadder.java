package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;

/**
 * Climb ladders and vines faster by boosting upward velocity.
 */
public class FastLadder extends Module {

    public static FastLadder INSTANCE;

    public FastLadder() {
        super("FastLadder", "Climb ladders faster", Category.PLAYER);
        addNumber("Speed", 0.3, 0.1, 1.0, 0.05, false);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!client.player.isClimbing()) return;

        double speed = parseDouble(getSetting("Speed"), 0.3);
        var vel = client.player.getVelocity();

        // Boost upward if pressing forward / jump
        if (client.options.jumpKey.isPressed() || client.options.forwardKey.isPressed()) {
            client.player.setVelocity(vel.x, speed, vel.z);
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
