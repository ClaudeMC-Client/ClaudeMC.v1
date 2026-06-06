package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Meteor Client-style Speed.
 * Vanilla mode: sets horizontal velocity directly from input direction each tick.
 * Strafe mode: scales existing XZ velocity up to the target speed, preserving
 *   sprint/jump momentum (same approach as Meteor's speed Strafe mode).
 */
public class Speed extends Module {

    public Speed() {
        super("Speed", "Move faster horizontally", Category.MOVEMENT);
        addNumber("Speed", 0.3, 0.1, 5.0, 0.05, false);
        addMode("Mode", "Vanilla", "Vanilla", "Strafe");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var opts = client.options;
        boolean moving = opts.forwardKey.isPressed() || opts.backKey.isPressed()
                      || opts.leftKey.isPressed()    || opts.rightKey.isPressed();
        if (!moving) return;
        if (client.player.isTouchingWater() || client.player.isInLava()) return;
        if (client.player.isGliding()) return;

        double spd = parseDouble(getSetting("Speed"), 0.3);

        if ("Strafe".equals(getSetting("Mode"))) {
            // Scale existing XZ momentum up to target speed without killing Y
            var vel = client.player.getVelocity();
            double current = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (current > 0.01 && current < spd) {
                double scale = spd / current;
                client.player.setVelocity(vel.x * scale, vel.y, vel.z * scale);
            }
        } else {
            // Vanilla: compute direction from keys and set velocity
            float yaw = (float) Math.toRadians(client.player.getYaw());
            double mx = 0, mz = 0;
            if (opts.forwardKey.isPressed()) { mx -= Math.sin(yaw); mz += Math.cos(yaw); }
            if (opts.backKey.isPressed())    { mx += Math.sin(yaw); mz -= Math.cos(yaw); }
            if (opts.leftKey.isPressed())    { mx -= Math.cos(yaw); mz -= Math.sin(yaw); }
            if (opts.rightKey.isPressed())   { mx += Math.cos(yaw); mz += Math.sin(yaw); }
            double len = Math.sqrt(mx * mx + mz * mz);
            if (len > 0) { mx /= len; mz /= len; }
            client.player.setVelocity(mx * spd, client.player.getVelocity().y, mz * spd);
        }
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) {
            var vel = c.player.getVelocity();
            c.player.setVelocity(0, vel.y, 0);
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
