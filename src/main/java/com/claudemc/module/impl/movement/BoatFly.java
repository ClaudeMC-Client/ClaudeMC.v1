package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.BoatEntity;

/**
 * BoatFly – fly while riding a boat by overriding boat velocity each tick.
 */
public class BoatFly extends Module {

    public static BoatFly INSTANCE;

    public BoatFly() {
        super("BoatFly", "Fly while riding a boat", Category.MOVEMENT);
        addSetting("Speed", "0.25");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!client.player.hasVehicle()) return;
        if (!(client.player.getVehicle() instanceof BoatEntity boat)) return;

        double speed = parseDouble(getSetting("Speed"), 0.25);
        var opts = client.options;

        float yawRad = (float) Math.toRadians(client.player.getYaw());
        double mx = 0, mz = 0, my = 0;

        if (opts.forwardKey.isPressed()) { mx -= Math.sin(yawRad); mz += Math.cos(yawRad); }
        if (opts.backKey.isPressed())    { mx += Math.sin(yawRad); mz -= Math.cos(yawRad); }
        if (opts.leftKey.isPressed())    { mx -= Math.cos(yawRad); mz -= Math.sin(yawRad); }
        if (opts.rightKey.isPressed())   { mx += Math.cos(yawRad); mz += Math.sin(yawRad); }
        if (opts.jumpKey.isPressed())    my = speed;
        if (opts.sneakKey.isPressed())   my = -speed;

        double len = Math.sqrt(mx * mx + mz * mz);
        if (len > 0) { mx /= len; mz /= len; }

        boat.setVelocity(mx * speed, my, mz * speed);
        boat.setYaw(client.player.getYaw());
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
