package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Overrides client-side world time so the sky/lighting renders at a fixed time.
 * Does not send any packets; purely visual.
 */
public class TimeChanger extends Module {

    public TimeChanger() {
        super("TimeChanger", "Locks client-side time to a fixed value (visual only)", Category.RENDER);
        addMode("Time", "Day", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom");
        addNumber("CustomTick", 6000, 0, 24000, 1000, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null) return;
        long time = getTargetTime();
        // 1.21.x replaced setTimeOfDay with setTime(worldTime, timeOfDay, shouldTickTimeOfDay).
        // shouldTickTimeOfDay=false locks the visual time until the next override.
        client.world.setTime(client.world.getTimeOfDay(), time, false);
    }

    @Override
    public void onDisable() {}

    private long getTargetTime() {
        return switch (getSetting("Time")) {
            case "Noon"     -> 6000L;
            case "Sunset"   -> 12000L;
            case "Night"    -> 14000L;
            case "Midnight" -> 18000L;
            case "Custom"   -> (long) parseInt(getSetting("CustomTick"), 6000);
            default         -> 1000L; // Day
        };
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
