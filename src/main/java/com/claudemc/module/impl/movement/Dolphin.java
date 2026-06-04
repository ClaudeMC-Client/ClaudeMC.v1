package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Dolphin – swim faster in water (like the Dolphin's Grace effect).
 */
public class Dolphin extends Module {

    public static Dolphin INSTANCE;

    public Dolphin() {
        super("Dolphin", "Swim faster in water like a dolphin", Category.MOVEMENT);
        addSetting("Speed", "0.40");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!client.player.isTouchingWater()) return;

        double speed = parseDouble(getSetting("Speed"), 0.40);
        var opts = client.options;

        boolean fwd   = opts.forwardKey.isPressed();
        boolean back  = opts.backKey.isPressed();
        boolean left  = opts.leftKey.isPressed();
        boolean right = opts.rightKey.isPressed();

        if (!fwd && !back && !left && !right) return;

        float yawRad = (float) Math.toRadians(client.player.getYaw());
        float pitchRad = (float) Math.toRadians(client.player.getPitch());
        double mx = 0, mz = 0;

        if (fwd)  { mx -= Math.sin(yawRad); mz += Math.cos(yawRad); }
        if (back) { mx += Math.sin(yawRad); mz -= Math.cos(yawRad); }
        if (left) { mx -= Math.cos(yawRad); mz -= Math.sin(yawRad); }
        if (right){ mx += Math.cos(yawRad); mz += Math.sin(yawRad); }

        double len = Math.sqrt(mx * mx + mz * mz);
        if (len > 0) { mx /= len; mz /= len; }

        double my = client.player.getVelocity().y;
        // Apply pitch-based vertical component when swimming forward
        if (fwd) {
            my = -Math.sin(pitchRad) * speed;
        }

        if (opts.jumpKey.isPressed())  my = speed;
        if (opts.sneakKey.isPressed()) my = -speed;

        client.player.setVelocity(mx * speed, my, mz * speed);
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
