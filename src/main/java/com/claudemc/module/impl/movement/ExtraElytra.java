package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * ExtraElytra – boost elytra flight without needing fireworks.
 * Applies forward velocity in the look direction while gliding.
 */
public class ExtraElytra extends Module {

    public static ExtraElytra INSTANCE;

    public ExtraElytra() {
        super("ExtraElytra", "Boost elytra flight without fireworks", Category.MOVEMENT);
        addSetting("Speed", "1.80");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!client.player.isGliding()) return;

        double speed = parseDouble(getSetting("Speed"), 1.80);

        float yawRad   = (float) Math.toRadians(client.player.getYaw());
        float pitchRad = (float) Math.toRadians(client.player.getPitch());

        double cosPitch = Math.cos(pitchRad);
        double vx = -Math.sin(yawRad) * cosPitch * speed;
        double vy = -Math.sin(pitchRad) * speed;
        double vz =  Math.cos(yawRad) * cosPitch * speed;

        client.player.setVelocity(vx, vy, vz);
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
