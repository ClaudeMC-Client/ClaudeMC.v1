package com.claudemc.hud;

import com.claudemc.ClaudeMCClient;
import com.claudemc.chat.ChatOverlay;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.impl.render.Radar;
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

    // Draggable stats strip position (bottom-left by default)
    public static int statsX = 4, statsY = -60; // negative = offset from bottom

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

    private boolean statsDragging = false;
    private int statsDragOffX, statsDragOffY;

    private void render(DrawContext ctx, RenderTickCounter tickCounter) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.inGameHud.getDebugHud().shouldShowDebugHud()) return;

        // Handle stats strip drag (right-click drag while no screen open)
        if (client.currentScreen == null) handleStatsDrag(client);

        renderWatermark(ctx, client);
        renderModuleList(ctx, client);
        renderCoords(ctx, client);
        renderStats(ctx, client);
        renderArmor(ctx, client);
        if (Radar.INSTANCE != null) Radar.INSTANCE.render(ctx, client);
        ChatOverlay.INSTANCE.render(ctx, client);
    }

    private void handleStatsDrag(MinecraftClient client) {
        long win = client.getWindow().getHandle();
        double scale = client.getWindow().getScaleFactor();
        int mx = (int)(client.mouse.getX() / scale);
        int my = (int)(client.mouse.getY() / scale);
        boolean rmb = org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                      == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (rmb) {
            if (!statsDragging && statsHitTest(mx, my, client)) {
                statsDragging = true;
                statsDragOffX = mx - statsX;
                int screenH = client.getWindow().getScaledHeight();
                int absY = statsY < 0 ? screenH + statsY : statsY;
                statsDragOffY = my - absY;
            }
            if (statsDragging) {
                statsX = mx - statsDragOffX;
                int newY = my - statsDragOffY;
                int screenH = client.getWindow().getScaledHeight();
                statsY = newY - screenH; // store as negative offset from bottom
            }
        } else {
            statsDragging = false;
        }
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

    // ── Stats strip (draggable) ──────────────────────────────────────────

    private void renderStats(DrawContext ctx, MinecraftClient client) {
        int screenH = client.getWindow().getScaledHeight();
        int x = statsX;
        int y = statsY < 0 ? screenH + statsY : statsY;

        int fps = MinecraftClient.getInstance().getCurrentFps();
        String tps = String.format("§7TPS §f%.1f", smoothedTps);
        int ping = getPing(client);
        String line = String.format("§7FPS §f%d  %s  §7Ping §f%dms", fps, tps, ping);
        int w = client.textRenderer.getWidth(line.replaceAll("§.", "")) + 4;

        ctx.fill(x - 2, y - 2, x + w, y + 10, 0x88000000);
        ctx.drawText(client.textRenderer, Text.literal(line), x, y, 0xFFFFFF, true);
    }

    /** Returns true if the point (mx, my) is over the stats strip. */
    public static boolean statsHitTest(int mx, int my, MinecraftClient client) {
        int screenH = client.getWindow().getScaledHeight();
        int x = statsX;
        int y = statsY < 0 ? screenH + statsY : statsY;
        return mx >= x - 2 && mx < x + 120 && my >= y - 2 && my < y + 12;
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
                // Non-damageable items (carved pumpkin, mob/player heads) have maxDamage 0 —
                // skip the durability % so we don't render a misleading "0%".
                if (stack.getMaxDamage() > 0) {
                    int dur = stack.getMaxDamage() - stack.getDamage();
                    int pct = (int)(100.0 * dur / stack.getMaxDamage());
                    int color = pct > 50 ? 0x44FF44 : (pct > 20 ? 0xFFAA00 : 0xFF4444);
                    ctx.drawText(client.textRenderer, Text.literal("§r" + pct + "%"), x, y + 10, color, false);
                }
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
