package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Overlay — renders a configurable solid-colour overlay on screen.
 * The Color setting accepts an ARGB hex string (e.g. "0x440000FF" for semi-transparent blue).
 */
public class Overlay extends Module {

    public static Overlay INSTANCE;

    public Overlay() {
        super("Overlay", "Renders a colored overlay on screen", Category.RENDER);
        addSetting("Color", "0x33FF0000");  // semi-transparent red
        addSetting("Opacity", "0.2");
        addMode("Mode", "Solid", "Solid", "Pulse", "Rainbow");
        INSTANCE = this;

        HudRenderCallback.EVENT.register((DrawContext ctx, RenderTickCounter tickCounter) -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null) return;

            int color = parseColor();
            String mode = INSTANCE.getSetting("Mode");

            if ("Rainbow".equals(mode)) {
                float hue = (System.currentTimeMillis() / 4000f) % 1f;
                int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
                int alpha = (int)(parseFloat(INSTANCE.getSetting("Opacity"), 0.2f) * 255) << 24;
                color = alpha | (rgb & 0x00FFFFFF);
            } else if ("Pulse".equals(mode)) {
                float phase = (float)(Math.sin(System.currentTimeMillis() / 800.0) * 0.5 + 0.5);
                float base  = parseFloat(INSTANCE.getSetting("Opacity"), 0.2f);
                int alpha   = (int)(base * phase * 255) << 24;
                color = alpha | (color & 0x00FFFFFF);
            }

            int w = client.getWindow().getScaledWidth();
            int h = client.getWindow().getScaledHeight();
            ctx.fill(0, 0, w, h, color);
        });
    }

    private int parseColor() {
        String s = getSetting("Color").trim();
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) return (int) Long.parseLong(s.substring(2), 16);
            if (s.startsWith("#")) return (int) Long.parseLong(s.substring(1), 16);
            return (int) Long.parseLong(s, 16);
        } catch (Exception e) {
            float op = parseFloat(getSetting("Opacity"), 0.2f);
            return ((int)(op * 255) << 24) | 0xFF0000;
        }
    }

    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return def; }
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
