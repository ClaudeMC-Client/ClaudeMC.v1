package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * HeadRoll: Rolls the player's head (changes yaw continuously for a rolling effect).
 */
public class HeadRoll extends Module {

    public static HeadRoll INSTANCE;

    private float rollAngle = 0f;

    public HeadRoll() {
        super("HeadRoll", "Rolls your player's head in a continuous motion", Category.MISC);
        INSTANCE = this;
        addNumber("Speed", 5, 1, 30, 1, true);
        addMode("Direction", "Right", "Right", "Left");
    }

    @Override
    public void onEnable() {
        rollAngle = 0f;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.setPitch(0);
        }
        rollAngle = 0f;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        float speed = parseFloat(getSetting("Speed"), 5);
        boolean left = "Left".equals(getSetting("Direction"));

        rollAngle += left ? -speed : speed;
        if (rollAngle > 180f) rollAngle -= 360f;
        if (rollAngle < -180f) rollAngle += 360f;

        // Apply roll via pitch (since Minecraft doesn't have true roll, we simulate with pitch oscillation)
        float simulatedRoll = (float) Math.sin(Math.toRadians(rollAngle)) * 85f;
        mc.player.setPitch(simulatedRoll);
    }

    private float parseFloat(String s, float d) {
        try { return Float.parseFloat(s); } catch (Exception e) { return d; }
    }
}
