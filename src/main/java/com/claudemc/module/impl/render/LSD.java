package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.awt.Color;

/**
 * LSD — cycles a translucent full-screen colour overlay through the rainbow,
 * creating a colour-shifting psychedelic visual effect.
 * The overlay is drawn via the HUD layer so it tints the entire screen.
 */
public class LSD extends Module {

    public static LSD INSTANCE;

    public LSD() {
        super("LSD", "Shifts colors on screen for a psychedelic visual effect", Category.RENDER);
        addSetting("Speed", "1.0");
        addSetting("Opacity", "0.3");
        INSTANCE = this;

        HudRenderCallback.EVENT.register((DrawContext drawContext, RenderTickCounter tickCounter) -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null) return;

            float speed   = parseFloat(INSTANCE.getSetting("Speed"),   1f);
            float opacity = parseFloat(INSTANCE.getSetting("Opacity"), 0.3f);
            opacity = Math.max(0f, Math.min(1f, opacity));

            float hue = (System.currentTimeMillis() / (6000f / Math.max(0.01f, speed))) % 1f;
            int rgb   = Color.HSBtoRGB(hue, 0.9f, 1f);
            int a     = (int) (opacity * 255) << 24;
            int color = (a) | (rgb & 0x00FFFFFF);

            int w = client.getWindow().getScaledWidth();
            int h = client.getWindow().getScaledHeight();
            drawContext.fill(0, 0, w, h, color);
        });
    }

    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
