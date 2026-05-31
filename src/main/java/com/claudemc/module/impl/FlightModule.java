package com.claudemc.module.impl;

import com.claudemc.module.Module;
import com.claudemc.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

public class FlightModule extends Module {

    private float flySpeed = 0.05f;

    public FlightModule() {
        super("Flight", "Creative-style flight in survival mode", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        PlayerAbilities ab = client.player.getAbilities();
        ab.allowFlying = true;
        client.player.sendAbilitiesUpdate();
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        PlayerAbilities ab = client.player.getAbilities();
        if (!ab.creativeMode) {
            ab.allowFlying = false;
            ab.flying = false;
        }
        client.player.sendAbilitiesUpdate();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        PlayerAbilities ab = client.player.getAbilities();
        // Keep flying allowed (server resets it each tick in survival)
        if (!ab.allowFlying) {
            ab.allowFlying = true;
        }
        ab.flySpeed = flySpeed;
    }

    public float getFlySpeed() { return flySpeed; }

    public void setFlySpeed(float speed) {
        this.flySpeed = Math.max(0.005f, Math.min(0.5f, speed));
    }
}
