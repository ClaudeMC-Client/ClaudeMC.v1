package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Phase – walk through blocks by keeping noClip active.
 * More aggressive than NoClip: also zeroes out collision-induced velocity
 * to maintain smooth movement through blocks.
 */
public class Phase extends Module {

    public static Phase INSTANCE;

    public Phase() {
        super("Phase", "Walk through blocks by toggling noClip", Category.MOVEMENT);
        addNumber("Speed", 0.20, 0.05, 2.0, 0.05, false);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) c.player.noClip = true;
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) c.player.noClip = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        client.player.noClip = true;

        double speed = Double.parseDouble(getSetting("Speed"));
        var opts = client.options;

        float yawRad   = (float) Math.toRadians(client.player.getYaw());
        float pitchRad = (float) Math.toRadians(client.player.getPitch());
        double mx = 0, mz = 0, my = 0;

        if (opts.forwardKey.isPressed()) { mx -= Math.sin(yawRad); mz += Math.cos(yawRad); }
        if (opts.backKey.isPressed())    { mx += Math.sin(yawRad); mz -= Math.cos(yawRad); }
        if (opts.leftKey.isPressed())    { mx -= Math.cos(yawRad); mz -= Math.sin(yawRad); }
        if (opts.rightKey.isPressed())   { mx += Math.cos(yawRad); mz += Math.sin(yawRad); }
        if (opts.jumpKey.isPressed())    my = speed;
        if (opts.sneakKey.isPressed())   my = -speed;

        double len = Math.sqrt(mx * mx + mz * mz);
        if (len > 0) { mx /= len; mz /= len; }

        if (mx != 0 || mz != 0 || my != 0 || opts.jumpKey.isPressed() || opts.sneakKey.isPressed()) {
            client.player.setVelocity(mx * speed, my, mz * speed);
        }
    }

}
