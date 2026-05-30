package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class Speed extends Module {

    public Speed() {
        super("Speed", "Move faster horizontally", Category.MOVEMENT);
        addSetting("Speed", "0.30");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var opts = client.options;
        boolean moving = opts.forwardKey.isPressed() || opts.backKey.isPressed()
                      || opts.leftKey.isPressed()    || opts.rightKey.isPressed();
        if (!moving || client.player.isTouchingWater() || client.player.isInLava()) return;

        double spd = parseDouble(getSetting("Speed"), 0.30);
        float yawRad = (float) Math.toRadians(client.player.getYaw());
        double mx = 0, mz = 0;

        if (opts.forwardKey.isPressed()) { mx -= Math.sin(yawRad); mz += Math.cos(yawRad); }
        if (opts.backKey.isPressed())    { mx += Math.sin(yawRad); mz -= Math.cos(yawRad); }
        if (opts.leftKey.isPressed())    { mx -= Math.cos(yawRad); mz -= Math.sin(yawRad); }
        if (opts.rightKey.isPressed())   { mx += Math.cos(yawRad); mz += Math.sin(yawRad); }

        double len = Math.sqrt(mx * mx + mz * mz);
        if (len > 0) { mx /= len; mz /= len; }

        var vel = client.player.getVelocity();
        client.player.setVelocity(mx * spd, vel.y, mz * spd);
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
