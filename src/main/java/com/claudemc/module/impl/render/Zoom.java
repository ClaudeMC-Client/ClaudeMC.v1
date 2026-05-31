package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Zoom — divides the current FOV by a factor while active.
 * The actual FOV override is applied in GameRendererMixin.
 * Mouse scroll wheel adjusts zoom level when enabled.
 */
public class Zoom extends Module {

    public static Zoom INSTANCE;

    private double zoomFactor = 4.0;

    public Zoom() {
        super("Zoom", "Zooms the camera (like a spyglass) while the module is active", Category.RENDER);
        addNumber("Factor", 4.0, 1.5, 20.0, 0.5, false);
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}

    public double getZoomFactor() {
        try { zoomFactor = Double.parseDouble(getSetting("Factor")); } catch (Exception ignored) {}
        return zoomFactor;
    }

    /** Called by scroll-wheel handling in MinecraftClientMixin or ClaudeMCClient */
    public void adjustZoom(double scroll) {
        zoomFactor = Math.max(1.5, Math.min(20.0, zoomFactor - scroll * 0.5));
        setSetting("Factor", String.format("%.1f", zoomFactor));
    }
}
