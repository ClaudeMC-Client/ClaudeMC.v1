package com.claudemc.hud;

import com.claudemc.ClaudeMCClient;
import com.claudemc.chat.ChatOverlay;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Meteor Client-style HUD renderer.
 *
 * Layout:
 *  Top-left  : Watermark  "ClaudeMC"
 *  Right side: Module list (coloured by category, Meteor style)
 *  Bottom-left: Coords / speed / biome
 *  Bottom-right: Armour / hotbar info
 */
public class HudManager {

    // TPS measured from WorldTimeUpdate packets
    private static long   lastTimePacket   = -1;
    private static double smoothedTps      = 20.0;

    public static void onWorldTimeUpdate() {
        long now = System.currentTimeMillis();
        if (lastTimePacket > 0) {
            double measured = Math.min(20.0, 20_000.0 / (now - lastTimePacket));
            smoothedTps = smoothedTps * 0.8 + measured * 0.2;
        }
        lastTimePacket = now;
    }

    public void register() {
        HudRenderCallback.EVENT.register(this::render);
    }

    // ────────────────────────────────────────────────────────────────────
    // Main render dispatch
    // ────────────────────────────────────────────────────────────────────

    private void render(DrawContext ctx, RenderTickCounter tickCounter) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

        renderWatermark(ctx, client);
        renderModuleList(ctx, client);
        renderCoords(ctx, client);
        renderStats(ctx, client);
        renderArmor(ctx, client);
        ChatOverlay.INSTANCE.render(ctx, client);
    }

    // ── Watermark ────────────────────────────────────────────────────────

    private void renderWatermark(DrawContext ctx, MinecraftClient client) {
        int x = 4, y = 4;
        ctx.fill(x - 2, y - 2, x + 82, y + 12, 0x99000000);
        ctx.fill(x - 2, y - 2, x - 1, y + 12, 0xFF4E6EF2);
        ctx.drawText(client.textRenderer, Text.literal("§b§lClaudeMC §7v2"), x, y, 0xFFFFFF, true);
    }

    // ── Right-side module list (Meteor style) ────────────────────────────

    private void renderModuleList(DrawContext ctx, MinecraftClient client) {
        int screenW = client.getWindow().getScaledWidth();
        int y = 4;

        // Collect enabled modules sorted by name length descending (longest first = Meteor style)
        var enabled = ClaudeMCClient.MODULES.getModules().stream()
            .filter(Module::isEnabled)
            .sorted(Comparator.comparingInt((Module m) -> client.textRenderer.getWidth(m.getName())).reversed())
            .toList();

        for (Module m : enabled) {
            String name = m.getName();
            int textW = client.textRenderer.getWidth(name);
            int x = screenW - textW - 6;
            int catColor = m.getCategory().color;

            // Background
            ctx.fill(x - 2, y, screenW, y + 10, 0x88000000);
            // Right accent bar in category colour
            ctx.fill(screenW - 2, y, screenW, y + 10, catColor);
            // Module name
            ctx.drawText(client.textRenderer, Text.literal(name), x, y + 1, catColor, true);

            y += 11;
        }
    }

    // ── Coords ───────────────────────────────────────────────────────────

    private void renderCoords(DrawContext ctx, MinecraftClient client) {
        int screenH = client.getWindow().getScaledHeight();
        int y = screenH - 40;

        var pos = client.player.getPos();
        String dim = client.world.getRegistryKey().getValue().getPath();
        boolean isNether = "the_nether".equals(dim);

        double ox = isNether ? pos.x * 8 : pos.x / 8;
        double oz = isNether ? pos.z * 8 : pos.z / 8;

        ctx.fill(2, y - 2, 160, y + 34, 0x88000000);
        ctx.drawText(client.textRenderer,
            Text.literal(String.format("§7XYZ §f%.1f §7/ §f%.1f §7/ §f%.1f", pos.x, pos.y, pos.z)),
            4, y, 0xFFFFFF, true);
        y += 10;
        ctx.drawText(client.textRenderer,
            Text.literal(String.format("§7%s §f%.1f §7/ §f%.1f",
                isNether ? "Overworld" : "Nether", ox, oz)),
            4, y, 0xFFFFFF, true);
        y += 10;
        ctx.drawText(client.textRenderer,
            Text.literal("§7Biome: §f" + getBiome(client)),
            4, y, 0xFFFFFF, true);
    }

    // ── Stats strip ──────────────────────────────────────────────────────

    private void renderStats(DrawContext ctx, MinecraftClient client) {
        int screenH = client.getWindow().getScaledHeight();
        int x = 4, y = screenH - 60;

        int fps = MinecraftClient.getInstance().getCurrentFps();
        String tps = String.format("§7TPS §f%.1f", smoothedTps);
        int ping = getPing(client);

        ctx.fill(x - 2, y - 2, 100, y + 12, 0x88000000);
        ctx.drawText(client.textRenderer,
            Text.literal(String.format("§7FPS §f%d  %s  §7Ping §f%dms", fps, tps, ping)),
            x, y, 0xFFFFFF, true);
    }

    // ── Armour display ───────────────────────────────────────────────────

    private void renderArmor(DrawContext ctx, MinecraftClient client) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int y = screenH - 50, x = screenW - 90;

        var armorItems = client.player.getInventory().armor;
        ctx.fill(x - 2, y - 2, screenW - 2, y + 18, 0x88000000);

        for (int i = 3; i >= 0; i--) {
            var stack = armorItems.get(i);
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, x, y);
                int dur = stack.getMaxDamage() - stack.getDamage();
                int pct = (int)(100.0 * dur / stack.getMaxDamage());
                int color = pct > 50 ? 0x44FF44 : (pct > 20 ? 0xFFAA00 : 0xFF4444);
                ctx.drawText(client.textRenderer, Text.literal("§r" + pct + "%"), x, y + 10, color, false);
                x += 22;
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String getBiome(MinecraftClient client) {
        var opt = client.world.getBiome(client.player.getBlockPos());
        return opt.getKey().map(k -> k.getValue().getPath()).orElse("unknown").replace("_", " ");
    }

    private int getPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return 0;
        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    public static double getEstimatedTps() { return smoothedTps; }
}
