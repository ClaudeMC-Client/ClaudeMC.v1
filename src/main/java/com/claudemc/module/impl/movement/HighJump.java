package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * HighJump – multiplies jump velocity so the player jumps much higher.
 */
public class HighJump extends Module {

    public static HighJump INSTANCE;

    private boolean wasOnGround = true;

    public HighJump() {
        super("HighJump", "Jump higher than normal", Category.MOVEMENT);
        addSetting("Height", "1.0");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        double height = parseDouble(getSetting("Height"), 1.0);
        var vel = client.player.getVelocity();

        boolean onGround = client.player.isOnGround();

        // Detect jump: was on ground, now has positive Y velocity
        if (!onGround && wasOnGround && vel.y > 0) {
            // Boost jump velocity
            client.player.setVelocity(vel.x, vel.y + height, vel.z);
        }

        wasOnGround = onGround;
    }

    @Override
    public void onDisable() {
        wasOnGround = true;
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
