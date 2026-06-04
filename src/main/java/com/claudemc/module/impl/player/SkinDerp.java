package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Randomises the player's head/body yaw to create a "derping" animation.
 * Purely cosmetic — does not send packets.
 */
public class SkinDerp extends Module {

    public static SkinDerp INSTANCE;

    private int tick = 0;

    public SkinDerp() {
        super("SkinDerp", "Randomizes skin pose (cosmetic)", Category.PLAYER);
        addNumber("Speed", 5, 1, 20, 1, true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        int speed = parseInt(getSetting("Speed"), 5);

        if (tick++ % speed == 0) {
            // Flip head yaw back and forth
            float newPitch = (float) (Math.random() * 180 - 90);
            client.player.setHeadYaw(client.player.getHeadYaw() + (float)(Math.random() * 60 - 30));
            // Only modify pitch visually; don't send it as a look packet here
        }
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
