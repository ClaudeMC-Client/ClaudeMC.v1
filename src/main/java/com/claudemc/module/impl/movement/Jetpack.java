package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Jetpack – fly upward when holding space, descend when holding shift.
 */
public class Jetpack extends Module {

    public static Jetpack INSTANCE;

    public Jetpack() {
        super("Jetpack", "Fly upward with space, descend with shift", Category.MOVEMENT);
        addSetting("Speed", "0.15");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        double speed = parseDouble(getSetting("Speed"), 0.15);
        var vel = client.player.getVelocity();

        double vy = vel.y;

        if (client.options.jumpKey.isPressed()) {
            vy += speed;
            if (vy > speed * 2) vy = speed * 2;
        } else if (client.options.sneakKey.isPressed()) {
            vy -= speed;
            if (vy < -speed * 2) vy = -speed * 2;
        }

        client.player.setVelocity(vel.x, vy, vel.z);
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
