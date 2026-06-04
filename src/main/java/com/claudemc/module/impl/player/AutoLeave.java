package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Automatically disconnects from the server when health drops below a threshold.
 */
public class AutoLeave extends Module {

    public static AutoLeave INSTANCE;

    public AutoLeave() {
        super("AutoLeave", "Automatically leaves server when health is low", Category.PLAYER);
        addNumber("MinHealth", 6, 1, 20, 0.5, false);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        double minHealth = parseDouble(getSetting("MinHealth"), 6.0);

        if (client.player.getHealth() <= (float) minHealth) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§cAutoLeave: health critical, disconnecting!"), false);
            client.getNetworkHandler().getConnection().disconnect(
                net.minecraft.text.Text.literal("AutoLeave"));
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
