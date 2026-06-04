package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * Derp: Makes the player's head spin wildly each tick.
 */
public class Derp extends Module {

    public static Derp INSTANCE;

    private final Random random = new Random();

    public Derp() {
        super("Derp", "Makes your head spin wildly", Category.MISC);
        INSTANCE = this;
        addNumber("Speed", 180, 10, 360, 10, true);
        addMode("Mode", "Random", "Random", "Spin", "Seizure");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        float speed = parseFloat(getSetting("Speed"), 180);
        String mode = getSetting("Mode");

        switch (mode) {
            case "Random" -> {
                float yaw = random.nextFloat() * 360f - 180f;
                float pitch = random.nextFloat() * 180f - 90f;
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            }
            case "Spin" -> {
                float currentYaw = mc.player.getYaw();
                mc.player.setYaw(currentYaw + speed);
            }
            case "Seizure" -> {
                mc.player.setYaw(mc.player.getYaw() + random.nextFloat() * speed * 2 - speed);
                mc.player.setPitch(mc.player.getPitch() + random.nextFloat() * 40 - 20);
                // Clamp pitch
                float pitch = mc.player.getPitch();
                if (pitch > 90) mc.player.setPitch(90);
                if (pitch < -90) mc.player.setPitch(-90);
            }
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.setPitch(0);
        }
    }

    private float parseFloat(String s, float d) {
        try { return Float.parseFloat(s); } catch (Exception e) { return d; }
    }
}
