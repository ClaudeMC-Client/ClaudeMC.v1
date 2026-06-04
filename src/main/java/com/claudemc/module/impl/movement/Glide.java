package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Glide – reduces fall speed so the player drifts down slowly.
 */
public class Glide extends Module {

    public static Glide INSTANCE;

    public Glide() {
        super("Glide", "Glide slowly when falling", Category.MOVEMENT);
        addSetting("FallSpeed", "0.03");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.isOnGround()) return;
        if (client.player.isTouchingWater() || client.player.isInLava()) return;
        if (client.player.isGliding()) return;

        double fallSpeed = parseDouble(getSetting("FallSpeed"), 0.03);
        var vel = client.player.getVelocity();

        if (vel.y < -fallSpeed) {
            client.player.setVelocity(vel.x, -fallSpeed, vel.z);
        }
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
