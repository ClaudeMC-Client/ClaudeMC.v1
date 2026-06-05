package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class Speed extends Module {

    public Speed() {
        super("Speed", "Move faster horizontally", Category.MOVEMENT);
        addNumber("Speed", 0.30, 0.1, 5.0, 0.05, false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var opts = client.options;
        boolean moving = opts.forwardKey.isPressed() || opts.backKey.isPressed()
                      || opts.leftKey.isPressed()    || opts.rightKey.isPressed();
        if (!moving || client.player.isTouchingWater() || client.player.isInLava()) return;

        double spd = Double.parseDouble(getSetting("Speed"));
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

    @Override
    public void onDisable() {
        var c = net.minecraft.client.MinecraftClient.getInstance();
        if (c.player != null) {
            var vel = c.player.getVelocity();
            c.player.setVelocity(0, vel.y, 0);
        }
    }

}
