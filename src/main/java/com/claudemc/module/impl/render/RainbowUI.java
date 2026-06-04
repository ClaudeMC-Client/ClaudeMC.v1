package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Makes GUI elements cycle through rainbow colors.
 * Provides a static {@link #getColor(float)} utility that returns an ARGB int
 * cycling through the hue spectrum. Other HUD/GUI modules can call this when
 * RainbowUI is enabled to tint their elements.
 */
public class RainbowUI extends Module {

    public static RainbowUI INSTANCE;

    private static long startTime = System.currentTimeMillis();

    public RainbowUI() {
        super("RainbowUI", "Makes GUI elements cycle through rainbow colors", Category.RENDER);
        addSetting("Speed", "1.0");
        INSTANCE = this;
    }

    /**
     * Returns an ARGB color int cycling through the rainbow.
     *
     * @param speed multiplier — 1.0 = one full cycle per ~6 seconds
     * @return ARGB packed integer
     */
    public static int getColor(float speed) {
        float hue = ((System.currentTimeMillis() - startTime) / 6000f * speed) % 1f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return (0xFF << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Convenience — uses the current Speed setting if INSTANCE is active.
     */
    public static int getColor() {
        float speed = 1f;
        if (INSTANCE != null && INSTANCE.isEnabled()) {
            try { speed = Float.parseFloat(INSTANCE.getSetting("Speed")); } catch (Exception ignored) {}
        }
        return getColor(speed);
    }

    /** Returns the current rainbow hue as 0–1 float. */
    public static float getHue() {
        float speed = 1f;
        if (INSTANCE != null && INSTANCE.isEnabled()) {
            try { speed = Float.parseFloat(INSTANCE.getSetting("Speed")); } catch (Exception ignored) {}
        }
        return ((System.currentTimeMillis() - startTime) / 6000f * speed) % 1f;
    }

    @Override
    public void onEnable() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
