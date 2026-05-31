package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Modifies game tick speed (timer).
 * 1.0 = normal, 2.0 = double speed, 0.5 = half speed.
 * The actual speed injection is done in MinecraftClientMixin.
 */
public class Timer extends Module {

    public static Timer INSTANCE;

    public Timer() {
        super("Timer", "Speed up or slow down game time", Category.WORLD);
        // Bounded so the GUI can't drive it to 0 (which would freeze game ticking).
        addNumber("Speed", 2.0, 0.1, 10.0, 0.1, false); // multiplier
        INSTANCE = this;
    }

    public float getSpeed() {
        float v;
        try { v = Float.parseFloat(getSetting("Speed")); }
        catch (Exception e) { return 2.0f; }
        return Math.max(0.1f, v);   // never 0 — would halt ticking
    }

    @Override public void onTick(MinecraftClient client) {}
}
