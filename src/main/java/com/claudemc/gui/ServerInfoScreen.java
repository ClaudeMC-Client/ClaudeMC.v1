package com.claudemc.gui;

import com.claudemc.server.ExploitFetcher;
import com.claudemc.server.ExploitMatcher;
import com.claudemc.server.ExploitMatcher.MatchResult;
import com.claudemc.server.RemoteExploit;
import com.claudemc.server.ServerInfo;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.*;

/**
 * Server Info screen — two tabs:
 *   [Plugins]  — detected brand + plugin channels with static vuln highlight
 *   [Exploits] — exploit list from remote DB, confirmed exploits glow green
 *
 * Colour coding:
 *   ★ Glowing green border = CONFIRMED (all requirements met on this server)
 *   Red    = CRITICAL
 *   Orange = HIGH
 *   Yellow = MEDIUM
 *   Grey   = PATCHED
 *   Green  = Safe / no known vulns
 */
public class ServerInfoScreen extends Screen {

    // ── Colours ──────────────────────────────────────────────────────────
    private static final int C_BG         = 0xE5101018;
    private static final int C_HEADER     = 0xFF18181F;
    private static final int C_ROW        = 0xFF111117;
    private static final int C_HOV        = 0xFF1C1C28;
    private static final int C_TEXT       = 0xFFEEEEEE;
    private static final int C_SUB        = 0xFF9CA3AF;
    private static final int C_SAFE       = 0xFF4ADE80;
    private static final int C_CONFIRMED  = 0xFF00FF88;  // bright confirmed glow
    private static final int C_CRITICAL   = 0xFFFF4444;
    private static final int C_HIGH       = 0xFFFF8800;
    private static final int C_MEDIUM     = 0xFFFFDD44;
    private static final int C_PATCHED    = 0xFF6B7280;

    private static final int ROW_H = 18;
    private static final int PAD   = 6;

    private enum Tab { PLUGINS, EXPLOITS }
    private Tab activeTab = Tab.EXPLOITS;  // default to exploits tab

    // Plugins tab
    private record PluginRow(String label, boolean isBrand, String severity) {}
    private final List<PluginRow> pluginRows = new ArrayList<>();

    // Exploits tab
    private List<MatchResult> exploitResults = new ArrayList<>();

    private int scrollOffset = 0;
    private int selectedIdx  = -1;  // selected row index for detail panel

    // Pulse animation for confirmed entries
    private float pulse = 0f;

    public ServerInfoScreen() {
        super(Text.literal("ClaudeMC — Server Info"));
    }

    @Override
    protected void init() {
        buildPluginRows();
        exploitResults = ExploitMatcher.match(ServerInfo.INSTANCE);
        scrollOffset = 0;
        selectedIdx  = -1;
    }

    private void buildPluginRows() {
        pluginRows.clear();
        String brand = ServerInfo.INSTANCE.getBrand();
        pluginRows.add(new PluginRow(brand, true, severityForName(brand)));
        List<String> plugins = ServerInfo.INSTANCE.getPlugins();
        if (plugins.isEmpty()) {
            pluginRows.add(new PluginRow("(no plugin channels detected yet)", false, null));
        } else {
            for (String p : plugins) pluginRows.add(new PluginRow(p, false, severityForName(p)));
        }
    }
    @Override public boolean shouldPause() { return false; }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        pulse = (pulse + delta * 0.05f) % (float) (Math.PI * 2);

        ctx.fill(0, 0, width, height, C_BG);

        // Title bar
        ctx.fill(0, 0, width, 20, C_HEADER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§fServer Info"), width / 2, 5, 0xFFFFFF);

        // DB status (top right)
        String status = ExploitFetcher.INSTANCE.getStatusMessage();
        ctx.drawText(textRenderer, Text.literal("§8" + status),
            width - textRenderer.getWidth(status) - 4, 6, C_SUB, false);

        // Tabs
        drawTabs(ctx, mx, my);

        int startY = 36;
        int footerH = 26;
        int contentH = height - startY - footerH;

        if (activeTab == Tab.PLUGINS) {
            drawPluginsTab(ctx, mx, my, startY, contentH);
        } else {
            drawExploitsTab(ctx, mx, my, startY, contentH);
        }

        drawFooter(ctx, mx, my);
    }

    private void drawTabs(DrawContext ctx, int mx, int my) {
        int tabY = 21;
        drawTab(ctx, mx, my, PAD,       tabY, "[Exploits]", activeTab == Tab.EXPLOITS, Tab.EXPLOITS);
        drawTab(ctx, mx, my, PAD + 90,  tabY, "[Plugins]",  activeTab == Tab.PLUGINS,  Tab.PLUGINS);
    }

    private void drawTab(DrawContext ctx, int mx, int my, int x, int y, String label, boolean active, Tab tab) {
        int w = 84, h = 13;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x, y, x + w, y + h, active ? 0xFF1A1A2E : (hover ? C_HOV : C_ROW));
        int accent = (tab == Tab.EXPLOITS) ? C_HIGH : 0xFF44AAFF;
        ctx.fill(x, y, x + 2, y + h, active ? accent : C_SUB);
        ctx.drawText(textRenderer, Text.literal((active ? "§f" : "§7") + label), x + 5, y + 3, C_TEXT, false);

        // Badge: count of confirmed exploits on Exploits tab
        if (tab == Tab.EXPLOITS) {
            long confirmed = exploitResults.stream().filter(MatchResult::confirmed).count();
            if (confirmed > 0) {
                String badge = "§c" + confirmed;
                ctx.drawText(textRenderer, Text.literal(badge), x + w - 14, y + 3, C_CRITICAL, false);
            }
        }
    }

    // ── Plugins tab ───────────────────────────────────────────────────────

    private void drawPluginsTab(DrawContext ctx, int mx, int my, int startY, int h) {
        int listW = (selectedIdx >= 0 && selectedIdx < pluginRows.size())
            ? width / 2 - PAD : width - PAD * 2;
        int visRows = h / ROW_H;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, pluginRows.size() - visRows)));

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= pluginRows.size()) break;
            PluginRow row = pluginRows.get(idx);
            int ry    = startY + i * ROW_H;
            boolean hover = mx >= PAD && mx < PAD + listW && my >= ry && my < ry + ROW_H;
            boolean sel   = selectedIdx == idx;

            int accent = accentForSeverity(row.severity());
            ctx.fill(PAD, ry, PAD + listW, ry + ROW_H, sel ? 0xFF1A1A2E : (hover ? C_HOV : C_ROW));
            ctx.fill(PAD, ry, PAD + 3, ry + ROW_H, accent);

            String prefix = row.isBrand() ? "§8[brand]  §f" : "§8[plugin] §7";
            ctx.drawText(textRenderer, Text.literal(prefix + row.label()), PAD + 6, ry + 5, C_TEXT, false);

            String badge = row.severity() == null ? "§aSAFE"
                : switch (row.severity()) {
                    case "CRITICAL" -> "§c● CRITICAL";
                    case "HIGH"     -> "§6● HIGH";
                    case "MEDIUM"   -> "§e● MEDIUM";
                    case "PATCHED"  -> "§8● PATCHED";
                    default         -> "§aSAFE";
                };
            int bw = textRenderer.getWidth(badge.replaceAll("§.", "")) + 4;
            ctx.drawText(textRenderer, Text.literal(badge), PAD + listW - bw, ry + 5, C_TEXT, false);
        }

        // Detail panel
        if (selectedIdx >= 0 && selectedIdx < pluginRows.size()) {
            drawPluginDetail(ctx, width / 2 + PAD, startY, width / 2 - PAD * 2, h,
                pluginRows.get(selectedIdx));
        }
    }

    private void drawPluginDetail(DrawContext ctx, int x, int y, int w, int h, PluginRow row) {
        ctx.fill(x, y, x + w, y + h, 0xEE0D0D14);
        ctx.fill(x, y, x + 2, y + h, accentForSeverity(row.severity()));
        int ty = y + 4;
        ctx.drawText(textRenderer, Text.literal("§f" + row.label()), x + 6, ty, C_TEXT, false);
        ty += 14;
        // Show matching exploits from the fetched DB
        boolean any = false;
        for (MatchResult mr : exploitResults) {
            String n = row.label().toLowerCase();
            boolean rel = matchesRow(mr.exploit(), n);
            if (!rel) continue;
            any = true;
            int col = severityColor(mr.exploit().severity);
            ctx.drawText(textRenderer, Text.literal(
                (mr.confirmed() ? "§a✓ " : "§8○ ") + "§f" + mr.exploit().name),
                x + 6, ty, col, false);
            ty += 10;
            if (mr.exploit().description != null) {
                String desc = mr.exploit().description;
                int maxW = w - 12;
                while (!desc.isEmpty() && ty < y + h - 10) {
                    String line = textRenderer.trimToWidth(desc, maxW);
                    ctx.drawText(textRenderer, Text.literal("§7 " + line), x + 6, ty, C_SUB, false);
                    desc = desc.substring(line.length()).stripLeading();
                    ty += 10;
                }
            }
            ty += 4;
        }
        if (!any) ctx.drawText(textRenderer, Text.literal("§aNo known exploits."), x + 6, ty, C_SAFE, false);
    }

    // ── Exploits tab ──────────────────────────────────────────────────────

    private void drawExploitsTab(DrawContext ctx, int mx, int my, int startY, int h) {
        if (!ExploitFetcher.INSTANCE.isLoaded()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Fetching exploit database…"), width / 2, startY + h / 2, C_SUB);
            return;
        }
        if (exploitResults.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§aNo matching exploits found for this server."), width / 2, startY + h / 2, C_SAFE);
            return;
        }

        int listW = (selectedIdx >= 0) ? width / 2 - PAD : width - PAD * 2;
        int visRows = h / ROW_H;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, exploitResults.size() - visRows)));

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= exploitResults.size()) break;
            MatchResult mr = exploitResults.get(idx);
            RemoteExploit e = mr.exploit();

            int ry    = startY + i * ROW_H;
            boolean hover = mx >= PAD && mx < PAD + listW && my >= ry && my < ry + ROW_H;
            boolean sel   = selectedIdx == idx;

            int bg = sel ? 0xFF1A1A2E : (hover ? C_HOV : C_ROW);
            ctx.fill(PAD, ry, PAD + listW, ry + ROW_H, bg);

            if (mr.confirmed()) {
                // Pulsing bright green border for confirmed exploits
                int alpha = (int) (180 + 75 * Math.sin(pulse + idx));
                int borderCol = (alpha << 24) | 0x00FF88;
                ctx.fill(PAD, ry, PAD + listW, ry + 1, borderCol);
                ctx.fill(PAD, ry + ROW_H - 1, PAD + listW, ry + ROW_H, borderCol);
                ctx.fill(PAD, ry, PAD + 3, ry + ROW_H, borderCol);
            } else {
                ctx.fill(PAD, ry, PAD + 3, ry + ROW_H, severityColor(e.severity));
            }

            // Confirmed badge or severity
            if (mr.confirmed()) {
                ctx.drawText(textRenderer, Text.literal("§a✓ CONFIRMED"), PAD + 6, ry + 5, C_CONFIRMED, false);
                String nameText = e.name;
                int nameX = PAD + 6 + textRenderer.getWidth("✓ CONFIRMED") + 8;
                ctx.drawText(textRenderer, Text.literal("§f" + nameText), nameX, ry + 5, C_TEXT, false);
            } else {
                String sevBadge = switch (e.severity == null ? "" : e.severity.toUpperCase()) {
                    case "CRITICAL" -> "§c CRITICAL ";
                    case "HIGH"     -> "§6 HIGH     ";
                    case "MEDIUM"   -> "§e MEDIUM   ";
                    case "PATCHED"  -> "§8 PATCHED  ";
                    default         -> "§7 UNKNOWN  ";
                };
                ctx.drawText(textRenderer, Text.literal(sevBadge + "§7" + e.name), PAD + 6, ry + 5, C_TEXT, false);
            }

            // Module hint on right
            if (e.module != null) {
                String hint = "§8[" + e.module + "]";
                int hw = textRenderer.getWidth(hint.replaceAll("§.", ""));
                ctx.drawText(textRenderer, Text.literal(hint), PAD + listW - hw - 4, ry + 5, C_SUB, false);
            }
        }

        // Detail panel
        if (selectedIdx >= 0 && selectedIdx < exploitResults.size()) {
            drawExploitDetail(ctx, width / 2 + PAD, startY, width / 2 - PAD * 2, h,
                exploitResults.get(selectedIdx));
        }
    }

    private void drawExploitDetail(DrawContext ctx, int x, int y, int w, int h, MatchResult mr) {
        RemoteExploit e = mr.exploit();
        ctx.fill(x, y, x + w, y + h, 0xEE0D0D14);
        int accent = mr.confirmed() ? C_CONFIRMED : severityColor(e.severity);
        ctx.fill(x, y, x + 2, y + h, accent);

        int ty = y + 4;
        String header = mr.confirmed() ? "§a✓ CONFIRMED — §f" + e.name : "§f" + e.name;
        ctx.drawText(textRenderer, Text.literal(header), x + 6, ty, accent, false);
        ty += 13;

        if (mr.confirmed()) {
            ctx.drawText(textRenderer,
                Text.literal("§aAll requirements met on this server!"),
                x + 6, ty, C_CONFIRMED, false);
            ty += 11;
        }

        // Severity
        ctx.drawText(textRenderer,
            Text.literal("§8Severity: " + colorForSeverity(e.severity) + (e.severity == null ? "?" : e.severity)),
            x + 6, ty, C_TEXT, false);
        ty += 11;

        // Module to use
        if (e.module != null) {
            ctx.drawText(textRenderer,
                Text.literal("§8Use module: §f" + e.module), x + 6, ty, C_TEXT, false);
            ty += 11;
        }

        // Techniques
        if (e.techniques != null && !e.techniques.isEmpty()) {
            ctx.drawText(textRenderer,
                Text.literal("§8Techniques: §7" + String.join(", ", e.techniques)),
                x + 6, ty, C_TEXT, false);
            ty += 11;
        }

        ty += 4;
        // Description word-wrap
        if (e.description != null) {
            String desc = e.description;
            int maxW = w - 12;
            while (!desc.isEmpty() && ty < y + h - 20) {
                String line = textRenderer.trimToWidth(desc, maxW);
                ctx.drawText(textRenderer, Text.literal("§7" + line), x + 6, ty, C_SUB, false);
                desc = desc.substring(line.length()).stripLeading();
                ty += 10;
            }
        }

        // Patched info
        if (e.patched_plugin_versions != null && !e.patched_plugin_versions.isEmpty()) {
            ty += 4;
            ctx.drawText(textRenderer, Text.literal("§8Patched in:"), x + 6, ty, C_TEXT, false);
            ty += 10;
            for (var entry : e.patched_plugin_versions.entrySet()) {
                ctx.drawText(textRenderer,
                    Text.literal("§7 " + entry.getKey() + " §a≥ " + entry.getValue()),
                    x + 6, ty, C_TEXT, false);
                ty += 10;
            }
        }
    }

    // ── Footer ────────────────────────────────────────────────────────────

    private void drawFooter(DrawContext ctx, int mx, int my) {
        int fy = height - 24;
        ctx.fill(0, fy, width, height, C_HEADER);

        drawBtn(ctx, mx, my, PAD,       fy + 4, 120, 14, "§f[Search dupedb.net]", C_HIGH);
        drawBtn(ctx, mx, my, PAD + 128, fy + 4, 70,  14, "§f[Refresh]",            C_SAFE);

        ctx.drawText(textRenderer,
            Text.literal("§8 ■ §aConfirmed  §8■ §cCritical  §8■ §6High  §8■ §eMedium  §8■ §8Patched"),
            PAD + 206, fy + 7, C_SUB, false);
    }

    private void drawBtn(DrawContext ctx, int mx, int my, int x, int y, int w, int h,
                         String label, int accent) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x, y, x + w, y + h, hover ? C_HOV : C_ROW);
        ctx.fill(x, y, x + 2, y + h, accent);
        ctx.drawText(textRenderer, Text.literal(label), x + 5, y + 3, C_TEXT, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(); double my = click.y(); int button = click.button();
        if (button != 0) return false;
        int x = (int) mx, y = (int) my;

        // Tabs
        if (y >= 21 && y < 34) {
            if (x >= PAD && x < PAD + 84)       { activeTab = Tab.EXPLOITS; scrollOffset = 0; selectedIdx = -1; return true; }
            if (x >= PAD + 90 && x < PAD + 174) { activeTab = Tab.PLUGINS;  scrollOffset = 0; selectedIdx = -1; return true; }
        }

        // Footer buttons
        int fy = height - 24;
        if (y >= fy + 4 && y < fy + 18) {
            if (x >= PAD && x < PAD + 120) {
                openDupedb();
                return true;
            }
            if (x >= PAD + 128 && x < PAD + 198) {
                init();
                return true;
            }
        }

        // Row clicks
        int startY = 36, footerH = 26;
        int h = height - startY - footerH;
        int visRows = h / ROW_H;
        List<?> rows = (activeTab == Tab.PLUGINS) ? pluginRows : exploitResults;
        int listW = (selectedIdx >= 0) ? width / 2 - PAD : width - PAD * 2;

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= rows.size()) break;
            int ry = startY + i * ROW_H;
            if (x >= PAD && x < PAD + listW && y >= ry && y < ry + ROW_H) {
                selectedIdx = (selectedIdx == idx) ? -1 : idx;
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
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(); int scanCode = input.scancode(); int modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            assert client != null;
            client.setScreen(new ClickGui());
            return true;
        }
        return super.keyPressed(input);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void openDupedb() {
        String brand = ServerInfo.INSTANCE.getBrand().toLowerCase();
        String url   = brand.contains("paper")  ? "https://dupedb.net/?software=paper"
                     : brand.contains("spigot") ? "https://dupedb.net/?software=spigot"
                     : brand.contains("purpur") ? "https://dupedb.net/?software=purpur"
                     : "https://dupedb.net";
        try { Util.getOperatingSystem().open(new URI(url)); } catch (Exception ignored) {}
    }

    private boolean matchesRow(RemoteExploit e, String nameLC) {
        if (e.requires_software != null)
            for (String s : e.requires_software)
                if (nameLC.contains(s.toLowerCase()) || s.toLowerCase().contains(nameLC)) return true;
        if (e.requires_plugins != null)
            for (String p : e.requires_plugins)
                if (nameLC.contains(p.toLowerCase()) || p.toLowerCase().contains(nameLC)) return true;
        return false;
    }

    private String severityForName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        for (MatchResult mr : exploitResults) {
            if (matchesRow(mr.exploit(), lower)) return mr.exploit().severity;
        }
        return null;
    }

    private int accentForSeverity(String s) {
        if (s == null) return C_SAFE;
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> C_CRITICAL;
            case "HIGH"     -> C_HIGH;
            case "MEDIUM"   -> C_MEDIUM;
            case "PATCHED"  -> C_PATCHED;
            default         -> C_SAFE;
        };
    }

    private int severityColor(String s) { return accentForSeverity(s); }

    private String colorForSeverity(String s) {
        if (s == null) return "§7";
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> "§c";
            case "HIGH"     -> "§6";
            case "MEDIUM"   -> "§e";
            case "PATCHED"  -> "§8";
            default         -> "§7";
        };
    }
}
