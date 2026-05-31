package com.claudemc.hud;

import com.claudemc.ClaudeMCClient;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public class HudOverlay {

    // TPS estimation via WorldTimeUpdate packets (updated from mixin)
    private static long lastTickPacketTime = -1;
    private static double estimatedTps = 20.0;

    /** Called from ClientPlayNetworkHandlerMixin each time the server sends a time-update. */
    public static void onWorldTimeUpdate() {
        long now = System.currentTimeMillis();
        if (lastTickPacketTime > 0) {
            long elapsed = now - lastTickPacketTime;
            // Server sends this every 20 ticks; elapsed ms → TPS
            double measured = Math.min(20.0, 20_000.0 / elapsed);
            estimatedTps = estimatedTps * 0.8 + measured * 0.2;
        }
        lastTickPacketTime = now;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            render(drawContext, MinecraftClient.getInstance());
        });
    }

    private static void render(DrawContext ctx, MinecraftClient client) {
        if (client.player == null || client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

        int fps = MinecraftClient.getInstance().getCurrentFps();
        String tpsStr = String.format("%.1f", estimatedTps);

        int x = 4, y = 4;
        int lineH = 10;

        // Collect active module names
        List<Module> active = ClaudeMCClient.MODULES.getModules()
            .stream().filter(Module::isEnabled).toList();

        int bgW = 108;
        int bgH = 4 + lineH        // title
                + lineH + lineH    // FPS + TPS
                + (active.isEmpty() ? 0 : 4 + active.size() * lineH)
                + 4;

        // Dark panel
        ctx.fill(x - 2, y - 2, x + bgW, y + bgH, 0xBB0A0A12);
        // Blue left accent bar
        ctx.fill(x - 2, y - 2, x - 1, y + bgH, 0xFF4E6EF2);

        // Title
        ctx.drawText(client.textRenderer, Text.literal("§b§lClaudeMC"), x, y, 0xFFFFFF, true);
        y += lineH + 2;

        ctx.drawText(client.textRenderer, Text.literal("§7FPS §f" + fps), x, y, 0xFFFFFF, true);
        y += lineH;
        ctx.drawText(client.textRenderer, Text.literal("§7TPS §f" + tpsStr), x, y, 0xFFFFFF, true);
        y += lineH + 4;

        for (Module m : active) {
            ctx.drawText(client.textRenderer, Text.literal("§a" + m.getName()), x, y, 0xFFFFFF, true);
            y += lineH;
        }
    }

    public static double getEstimatedTps() { return estimatedTps; }
}
