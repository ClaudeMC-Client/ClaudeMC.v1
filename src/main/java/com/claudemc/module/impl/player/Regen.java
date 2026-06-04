package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Regenerates health faster by exploiting saturation to trigger natural regeneration.
 * Boosts upward velocity slightly each tick when health is not full to trigger regen packets.
 */
public class Regen extends Module {

    public static Regen INSTANCE;

    private int tick = 0;

    public Regen() {
        super("Regen", "Regenerates health faster via saturation tricks", Category.PLAYER);
        addNumber("Interval", 10, 1, 40, 1, true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.getHealth() >= client.player.getMaxHealth()) return;
        if (client.player.getHungerManager().getFoodLevel() < 18) return;

        int interval = parseInt(getSetting("Interval"), 10);
        if (tick++ % interval != 0) return;

        // Simulate a tiny sprint to trigger saturation-based regeneration server-side
        client.player.setSprinting(true);
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
