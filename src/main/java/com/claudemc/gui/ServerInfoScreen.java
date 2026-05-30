package com.claudemc.gui;

import com.claudemc.server.ServerInfo;
import com.claudemc.server.VulnDb;
import com.claudemc.server.VulnDb.Severity;
import com.claudemc.server.VulnDb.VulnEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.*;

/**
 * Server Info screen — shows detected server brand + plugin channels
 * and highlights entries with known vulnerabilities.
 *
 * Colour coding:
 *   Red    = CRITICAL
 *   Orange = HIGH
 *   Yellow = MEDIUM
 *   Grey   = PATCHED (was vulnerable, now fixed)
 *   Green  = No known vulnerabilities
 */
public class ServerInfoScreen extends Screen {

    // ── Colours ──────────────────────────────────────────────────────────
    private static final int C_BG       = 0xE5101018;
    private static final int C_HEADER   = 0xFF18181F;
    private static final int C_ROW      = 0xFF111117;
    private static final int C_HOV      = 0xFF1C1C28;
    private static final int C_TEXT     = 0xFFEEEEEE;
    private static final int C_SUB      = 0xFF9CA3AF;
    private static final int C_SAFE     = 0xFF4ADE80;
    private static final int C_CRITICAL = 0xFFFF4444;
    private static final int C_HIGH     = 0xFFFF8800;
    private static final int C_MEDIUM   = 0xFFFFDD44;
    private static final int C_PATCHED  = 0xFF6B7280;

    private static final int ROW_H = 18;
    private static final int PAD   = 6;

    // Built once in init
    private record Row(String label, boolean isBrand, List<VulnEntry> vulns) {}
    private final List<Row> rows = new ArrayList<>();

    private int scrollOffset = 0;
    private int hoveredRow   = -1;
    // If a row is selected, show its vuln details in a side panel
    private int selectedRow  = -1;

    public ServerInfoScreen() {
        super(Text.literal("ClaudeMC — Server Info"));
    }

    @Override
    protected void init() {
        rows.clear();
        String brand = ServerInfo.INSTANCE.getBrand();
        rows.add(new Row(brand, true, VulnDb.lookup(brand)));

        List<String> plugins = ServerInfo.INSTANCE.getPlugins();
        if (plugins.isEmpty()) {
            rows.add(new Row("(no plugin channels detected)", false, List.of()));
        } else {
            for (String p : plugins) {
                rows.add(new Row(p, false, VulnDb.lookup(p)));
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);

        // Title bar
        ctx.fill(0, 0, width, 20, C_HEADER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§fServer Info  §8|  §7click a row for details  §8|  §7Esc = back"),
            width / 2, 5, 0xFFFFFF);

        int listW = (selectedRow >= 0) ? width / 2 - PAD : width - PAD * 2;
        int startY = 24;
        int visH   = height - startY - 26;
        int visRows = visH / ROW_H;

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visRows)));
        hoveredRow = -1;

        // List
        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= rows.size()) break;

            Row row = rows.get(idx);
            int ry = startY + i * ROW_H;
            boolean hover  = mx >= PAD && mx < PAD + listW && my >= ry && my < ry + ROW_H;
            boolean sel    = selectedRow == idx;

            if (hover) hoveredRow = idx;

            int accent = accentColor(row);
            int bg     = sel ? 0xFF1A1A2E : (hover ? C_HOV : C_ROW);
            ctx.fill(PAD, ry, PAD + listW, ry + ROW_H, bg);
            ctx.fill(PAD, ry, PAD + 3, ry + ROW_H, accent);  // left stripe

            // Label
            String prefix = row.isBrand() ? "§8[brand] §f" : "§8[plugin] §7";
            ctx.drawText(textRenderer, Text.literal(prefix + row.label()),
                PAD + 6, ry + 5, C_TEXT, false);

            // Severity badge (rightmost)
            if (!row.vulns().isEmpty()) {
                Severity worst = worstSeverity(row.vulns());
                String badge = switch (worst) {
                    case CRITICAL -> "§c● CRITICAL";
                    case HIGH     -> "§6● HIGH";
                    case MEDIUM   -> "§e● MEDIUM";
                    case PATCHED  -> "§8● PATCHED";
                };
                int bw = textRenderer.getWidth(badge.replaceAll("§.", "")) + 4;
                ctx.drawText(textRenderer, Text.literal(badge),
                    PAD + listW - bw, ry + 5, C_TEXT, false);
            } else {
                ctx.drawText(textRenderer, Text.literal("§a● SAFE"),
                    PAD + listW - 44, ry + 5, C_SAFE, false);
            }
        }

        // Detail panel (right half)
        if (selectedRow >= 0 && selectedRow < rows.size()) {
            drawDetailPanel(ctx, width / 2 + PAD, startY, width / 2 - PAD * 2, visH,
                rows.get(selectedRow));
        }

        // Footer
        ctx.fill(0, height - 24, width, height, C_HEADER);
        drawButton(ctx, mx, my, PAD, height - 20, 120, 14,
            "§f[Search dupedb.net]", 0xFF1C1C28, 0xFFFF8800);
        drawButton(ctx, mx, my, PAD + 128, height - 20, 80, 14,
            "§f[Refresh]", 0xFF1C1C28, 0xFF4ADE80);
        ctx.drawText(textRenderer,
            Text.literal("§8Legend:  §c■ CRITICAL  §6■ HIGH  §e■ MEDIUM  §8■ PATCHED  §a■ SAFE"),
            PAD + 216, height - 17, C_SUB, false);
    }

    private void drawDetailPanel(DrawContext ctx, int x, int y, int w, int h, Row row) {
        ctx.fill(x, y, x + w, y + h, 0xEE0D0D14);
        ctx.fill(x, y, x + 2, y + h, accentColor(row));

        int ty = y + 4;
        ctx.drawText(textRenderer, Text.literal("§f" + row.label()), x + 6, ty, C_TEXT, false);
        ty += 14;

        if (row.vulns().isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("§aNo known vulnerabilities."), x + 6, ty, C_SAFE, false);
            return;
        }

        for (VulnEntry v : row.vulns()) {
            if (ty > y + h - 12) break;

            int col = switch (v.severity()) {
                case CRITICAL -> C_CRITICAL;
                case HIGH     -> C_HIGH;
                case MEDIUM   -> C_MEDIUM;
                case PATCHED  -> C_PATCHED;
            };
            ctx.drawText(textRenderer,
                Text.literal("§8[" + v.severity().name() + "] §f" + v.affectedVersions()),
                x + 6, ty, col, false);
            ty += 11;

            // Word-wrap description
            String desc = v.description();
            int maxW = w - 12;
            while (!desc.isEmpty() && ty < y + h - 12) {
                String line = textRenderer.trimToWidth(desc, maxW);
                ctx.drawText(textRenderer, Text.literal("§7 " + line), x + 6, ty, C_SUB, false);
                desc = desc.substring(line.length()).stripLeading();
                ty += 10;
            }

            ctx.drawText(textRenderer,
                Text.literal("§8Patched: §a" + v.patchedIn()),
                x + 6, ty, C_TEXT, false);
            ty += 14;
        }
    }

    private void drawButton(DrawContext ctx, int mx, int my,
                            int x, int y, int w, int h,
                            String label, int bg, int accent) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x, y, x + w, y + h, hover ? C_HOV : bg);
        ctx.fill(x, y, x + 2, y + h, accent);
        ctx.drawText(textRenderer, Text.literal(label), x + 5, y + 3, C_TEXT, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int x = (int) mx, y = (int) my;

        // Footer buttons
        if (y >= height - 20 && y < height - 6) {
            if (x >= PAD && x < PAD + 120) {
                // Open dupedb.net in browser
                String query = buildDupedbnUrl();
                try { Util.getOperatingSystem().open(new URI(query)); } catch (Exception ignored) {}
                return true;
            }
            if (x >= PAD + 128 && x < PAD + 208) {
                init(); // refresh
                return true;
            }
        }

        // Row click → select for detail
        int startY = 24;
        int visH   = height - startY - 26;
        int visRows = visH / ROW_H;
        int listW  = (selectedRow >= 0) ? width / 2 - PAD : width - PAD * 2;

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= rows.size()) break;
            int ry = startY + i * ROW_H;
            if (x >= PAD && x < PAD + listW && y >= ry && y < ry + ROW_H) {
                selectedRow = (selectedRow == idx) ? -1 : idx;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scrollOffset -= (int) vy;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            assert client != null;
            client.setScreen(new ClickGui());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int accentColor(Row row) {
        if (row.vulns().isEmpty()) return C_SAFE;
        return switch (worstSeverity(row.vulns())) {
            case CRITICAL -> C_CRITICAL;
            case HIGH     -> C_HIGH;
            case MEDIUM   -> C_MEDIUM;
            case PATCHED  -> C_PATCHED;
        };
    }

    private Severity worstSeverity(List<VulnEntry> vulns) {
        Severity worst = Severity.PATCHED;
        for (VulnEntry v : vulns) {
            if (v.severity().ordinal() < worst.ordinal()) worst = v.severity();
        }
        return worst;
    }

    private String buildDupedbnUrl() {
        // Open dupedb.net filtered to the server brand if we can identify it
        String brand = ServerInfo.INSTANCE.getBrand().toLowerCase();
        if (brand.contains("paper"))  return "https://dupedb.net/?software=paper";
        if (brand.contains("spigot")) return "https://dupedb.net/?software=spigot";
        if (brand.contains("purpur")) return "https://dupedb.net/?software=purpur";
        return "https://dupedb.net";
    }
}
