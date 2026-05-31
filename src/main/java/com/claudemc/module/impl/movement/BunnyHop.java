package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class BunnyHop extends Module {

    public BunnyHop() {
        super("BunnyHop", "Auto-jumps every time you land to maintain sprint speed", Category.MOVEMENT);
        addNumber("SpeedBoost", 0.02, 0.0, 0.2, 0.01, false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        if (!client.player.isSprinting()) return;

        if (client.player.isOnGround()) {
            client.player.jump();
            double boost = parseDouble(getSetting("SpeedBoost"), 0.02);
            var vel = client.player.getVelocity();
            var look = client.player.getRotationVec(1.0f);
            client.player.setVelocity(vel.x + look.x * boost, vel.y, vel.z + look.z * boost);
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}
