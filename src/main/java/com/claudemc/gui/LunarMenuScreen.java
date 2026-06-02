package com.claudemc.gui;

import com.claudemc.ClaudeMCClient;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.impl.AimAssistModule;
import com.claudemc.module.impl.movement.Flight;
import com.claudemc.module.impl.render.ESP;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lunar Client-inspired dark-themed module menu.
 * Open/close with the [.] keybind.
 */
public class LunarMenuScreen extends Screen {

    // ── Palette ──────────────────────────────────────────────────────────
    private static final int C_BG          = 0xEE0A0A12; // main panel bg
    private static final int C_SIDEBAR     = 0xEE0D0D1A; // left sidebar
    private static final int C_HEADER      = 0xFF0B0B18; // top bar
    private static final int C_ACCENT      = 0xFF4E6EF2; // blue accent
    private static final int C_ACCENT_DIM  = 0x664E6EF2; // faded blue
    private static final int C_CARD        = 0xFF141420; // module card bg
    private static final int C_CARD_HOV    = 0xFF1C1C30; // card hovered
    private static final int C_ENABLED     = 0xFF4ADE80; // green dot / text
    private static final int C_DISABLED    = 0xFF4B5563; // gray
    private static final int C_TEXT        = 0xFFEEEEEE;
    private static final int C_SUBTEXT     = 0xFF9CA3AF;
    private static final int C_DIVIDER     = 0xFF1E1E30;
    private static final int C_BTN         = 0xFF232338;
    private static final int C_BTN_HOV     = 0xFF2D2D50;
    private static final int C_BTN_ACT     = 0xFF4E6EF2;

    // ── Layout ───────────────────────────────────────────────────────────
    private static final int PANEL_W  = 580;
    private static final int PANEL_H  = 380;
    private static final int SIDEBAR_W = 110;
    private static final int HEADER_H  = 28;
    private static final int CARD_W    = 142;
    private static final int CARD_H    = 64;
    private static final int CARD_PAD  = 8;
    private static final int SETTINGS_H = 130;

    private int px, py; // panel top-left corner

    private final Map<Category, List<Module>> byCategory = new LinkedHashMap<>();
    private final List<Category> categories = new ArrayList<>();

    private Category selectedCategory;
    private Module selectedModule;

    // Clickable regions (rebuilt each render)
    private final List<int[]> categoryRects = new ArrayList<>();
    private final List<int[]> moduleRects   = new ArrayList<>();
    private final List<Runnable> moduleActions = new ArrayList<>();
    private final List<int[]> settingRects  = new ArrayList<>();
    private final List<Runnable> settingActions = new ArrayList<>();

    public LunarMenuScreen() {
        super(Text.literal("ClaudeMC"));

        for (Module m : ClaudeMCClient.MODULES.getModules()) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }
        categories.addAll(byCategory.keySet());
        if (!categories.isEmpty()) selectedCategory = categories.get(0);
    }
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void init() {
        px = (width  - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Semi-transparent world overlay
        ctx.fill(0, 0, width, height, 0x88000000);

        categoryRects.clear();
        moduleRects.clear();
        moduleActions.clear();
        settingRects.clear();
        settingActions.clear();

        drawPanel(ctx, mx, my);
        drawHeader(ctx, mx, my);
        drawSidebar(ctx, mx, my);
        drawModuleGrid(ctx, mx, my);
        if (selectedModule != null) drawSettings(ctx, mx, my);

        // Don't call super.render — we don't want vanilla widgets
    }

    // ── Panel shell ──────────────────────────────────────────────────────

    private void drawPanel(DrawContext ctx, int mx, int my) {
        // Shadow
        ctx.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0x55000000);
        // Body
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, C_BG);
        // Border
        drawBorder(ctx, px, py, PANEL_W, PANEL_H, C_ACCENT_DIM);
    }

    private void drawHeader(DrawContext ctx, int mx, int my) {
        ctx.fill(px, py, px + PANEL_W, py + HEADER_H, C_HEADER);
        ctx.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, C_ACCENT);

        String title = "ClaudeMC";
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, Text.literal("§b§l" + title), px + (PANEL_W - tw) / 2, py + 9, C_TEXT, true);

        // Close [×]
        int cx = px + PANEL_W - 20, cy = py + 5;
        boolean hov = inRect(mx, my, cx, cy, 16, 16);
        ctx.fill(cx, cy, cx + 16, cy + 16, hov ? 0xFF8B1C1C : 0xFF3A1010);
        ctx.drawText(textRenderer, Text.literal("§c×"), cx + 4, cy + 4, C_TEXT, true);
    }

    // ── Left sidebar ─────────────────────────────────────────────────────

    private void drawSidebar(DrawContext ctx, int mx, int my) {
        int sx = px, sy = py + HEADER_H;
        int sh = PANEL_H - HEADER_H;
        ctx.fill(sx, sy, sx + SIDEBAR_W, sy + sh, C_SIDEBAR);
        ctx.fill(sx + SIDEBAR_W - 1, sy, sx + SIDEBAR_W, sy + sh, C_DIVIDER);

        int y = sy + 12;
        ctx.drawText(textRenderer, Text.literal("§7MODULES"), sx + 8, y, C_SUBTEXT, false);
        y += 14;

        for (Category cat : categories) {
            boolean sel = cat.equals(selectedCategory);
            boolean hov = inRect(mx, my, sx, y - 2, SIDEBAR_W - 1, 16);
            int bg = sel ? C_ACCENT_DIM : (hov ? 0x331A1A40 : 0x00000000);
            ctx.fill(sx, y - 2, sx + SIDEBAR_W - 1, y + 14, bg);
            if (sel) ctx.fill(sx, y - 2, sx + 2, y + 14, C_ACCENT); // accent strip
            ctx.drawText(textRenderer, Text.literal((sel ? "§f" : "§7") + cat.displayName), sx + 10, y + 2, C_TEXT, false);

            categoryRects.add(new int[]{sx, y - 2, SIDEBAR_W - 1, 16, categories.indexOf(cat)});
            y += 20;
        }

        // Stats at bottom
        int statY = sy + sh - 36;
        ctx.fill(sx, statY - 4, sx + SIDEBAR_W - 1, statY - 3, C_DIVIDER);
        int fps = net.minecraft.client.MinecraftClient.getInstance().getCurrentFps();
        double tps = com.claudemc.hud.HudManager.getEstimatedTps();
        ctx.drawText(textRenderer, Text.literal("§7FPS §f" + fps), sx + 8, statY, C_TEXT, false);
        ctx.drawText(textRenderer, Text.literal("§7TPS §f" + String.format("%.1f", tps)), sx + 8, statY + 12, C_TEXT, false);
    }

    // ── Module grid ──────────────────────────────────────────────────────

    private void drawModuleGrid(DrawContext ctx, int mx, int my) {
        int gx = px + SIDEBAR_W + 10;
        int gy = py + HEADER_H + 10;
        int gridW = PANEL_W - SIDEBAR_W - 20;

        int settingsH = (selectedModule != null) ? SETTINGS_H + 10 : 0;
        int gridH = PANEL_H - HEADER_H - settingsH - 10;

        List<Module> mods = byCategory.getOrDefault(selectedCategory, List.of());
        int cols = Math.max(1, (gridW + CARD_PAD) / (CARD_W + CARD_PAD));

        int idx = 0;
        for (Module m : mods) {
            int col = idx % cols;
            int row = idx / cols;
            int cx = gx + col * (CARD_W + CARD_PAD);
            int cy = gy + row * (CARD_H + CARD_PAD);

            if (cy + CARD_H > gy + gridH) break; // clip

            boolean hov = inRect(mx, my, cx, cy, CARD_W, CARD_H);
            boolean sel = m == selectedModule;

            int cardBg = sel ? 0xFF1A2050 : (hov ? C_CARD_HOV : C_CARD);
            ctx.fill(cx, cy, cx + CARD_W, cy + CARD_H, cardBg);
            drawBorder(ctx, cx, cy, CARD_W, CARD_H, sel ? C_ACCENT : C_DIVIDER);

            if (m.isEnabled()) {
                // Green indicator strip at top
                ctx.fill(cx + 2, cy + 2, cx + CARD_W - 2, cy + 4, C_ENABLED);
            }

            // Module name
            ctx.drawText(textRenderer, Text.literal("§f§l" + m.getName()), cx + 8, cy + 10, C_TEXT, true);

            // Description (word-wrap to card width)
            String desc = m.getDescription();
            if (textRenderer.getWidth(desc) > CARD_W - 16) {
                desc = textRenderer.trimToWidth(desc, CARD_W - 16) + "…";
            }
            ctx.drawText(textRenderer, Text.literal("§7" + desc), cx + 8, cy + 22, C_SUBTEXT, false);

            // Toggle button
            String label = m.isEnabled() ? "§aON" : "§cOFF";
            int bx = cx + CARD_W - 34, by = cy + CARD_H - 18;
            boolean bHov = inRect(mx, my, bx, by, 28, 12);
            ctx.fill(bx, by, bx + 28, by + 12, bHov ? C_BTN_HOV : C_BTN);
            drawBorder(ctx, bx, by, 28, 12, m.isEnabled() ? C_ENABLED : C_DISABLED);
            ctx.drawText(textRenderer, Text.literal(label), bx + 4, by + 2, C_TEXT, false);

            int fi = idx;
            moduleRects.add(new int[]{cx, cy, CARD_W, CARD_H, fi});
            idx++;
        }
    }

    // ── Settings panel ───────────────────────────────────────────────────

    private void drawSettings(DrawContext ctx, int mx, int my) {
        int sx = px + SIDEBAR_W + 10;
        int sy = py + PANEL_H - SETTINGS_H - 4;
        int sw = PANEL_W - SIDEBAR_W - 20;

        ctx.fill(sx, sy - 2, sx + sw, sy + SETTINGS_H, C_HEADER);
        drawBorder(ctx, sx, sy - 2, sw, SETTINGS_H + 2, C_DIVIDER);
        ctx.fill(sx, sy - 2, sx + sw, sy, C_ACCENT);

        ctx.drawText(textRenderer, Text.literal("§f⚙ §7" + selectedModule.getName() + " Settings"),
            sx + 8, sy + 4, C_TEXT, false);

        int y = sy + 18;

        if (selectedModule instanceof Flight)         drawFlightSettings(ctx, mx, my, sx, y, sw, selectedModule);
        else if (selectedModule instanceof ESP)       drawEspSettings(ctx, mx, my, sx, y, sw, selectedModule);
        else if (selectedModule instanceof AimAssistModule) drawAimSettings(ctx, mx, my, sx, y, sw, selectedModule);
    }

    // ── Flight settings ──────────────────────────────────────────────────

    private void drawFlightSettings(DrawContext ctx, int mx, int my, int sx, int sy, int sw, Module fm) {
        float curSpeed = parseFloat(fm.getSetting("Speed"), 0.10f);
        ctx.drawText(textRenderer, Text.literal("§7Speed: §f" + String.format("%.3f", curSpeed)), sx + 8, sy, C_TEXT, false);
        sy += 12;

        float[] speeds = {0.01f, 0.05f, 0.10f, 0.20f, 0.50f};
        String[] labels = {"Slow", "Normal", "Fast", "Faster", "Ultra"};
        int bx = sx + 8;
        for (int i = 0; i < speeds.length; i++) {
            boolean sel = Math.abs(curSpeed - speeds[i]) < 0.001f;
            boolean hov = inRect(mx, my, bx, sy, 48, 14);
            ctx.fill(bx, sy, bx + 48, sy + 14, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            drawBorder(ctx, bx, sy, 48, 14, sel ? C_ACCENT : C_DIVIDER);
            ctx.drawText(textRenderer, Text.literal(labels[i]), bx + 4, sy + 3, C_TEXT, false);
            final String sv = String.valueOf(speeds[i]);
            settingRects.add(new int[]{bx, sy, 48, 14});
            settingActions.add(() -> fm.setSetting("Speed", sv));
            bx += 54;
        }
    }

    // ── ESP settings ─────────────────────────────────────────────────────

    private void drawEspSettings(DrawContext ctx, int mx, int my, int sx, int sy, int sw, Module em) {
        ctx.drawText(textRenderer, Text.literal("§7Show:"), sx + 8, sy, C_TEXT, false);
        sy += 12;

        String[] filters = {"All", "Players", "Hostile"};
        String[] fLabels = {"All Entities", "Players Only", "Hostiles Only"};
        String curFilter = em.getSetting("Filter");
        int bx = sx + 8;
        for (int i = 0; i < filters.length; i++) {
            boolean sel = filters[i].equals(curFilter);
            boolean hov = inRect(mx, my, bx, sy, 78, 14);
            ctx.fill(bx, sy, bx + 78, sy + 14, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            drawBorder(ctx, bx, sy, 78, 14, sel ? C_ACCENT : C_DIVIDER);
            ctx.drawText(textRenderer, Text.literal(fLabels[i]), bx + 4, sy + 3, C_TEXT, false);
            final String f = filters[i];
            settingRects.add(new int[]{bx, sy, 78, 14});
            settingActions.add(() -> em.setSetting("Filter", f));
            bx += 84;
        }
    }

    // ── AimAssist settings ───────────────────────────────────────────────

    private void drawAimSettings(DrawContext ctx, int mx, int my, int sx, int sy, int sw, Module am) {
        // Target mode buttons
        ctx.drawText(textRenderer, Text.literal("§7Target:"), sx + 8, sy, C_TEXT, false);
        sy += 12;

        String[] targets = {"Players", "Hostile+Players", "Hostile", "All"};
        String[] tLabels = {"Players", "Hostile+P.", "Hostile", "All"};
        String curTarget = am.getSetting("Target");
        int bx = sx + 8;
        for (int i = 0; i < targets.length; i++) {
            boolean sel = targets[i].equals(curTarget);
            boolean hov = inRect(mx, my, bx, sy, 62, 14);
            ctx.fill(bx, sy, bx + 62, sy + 14, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            drawBorder(ctx, bx, sy, 62, 14, sel ? C_ACCENT : C_DIVIDER);
            ctx.drawText(textRenderer, Text.literal(tLabels[i]), bx + 4, sy + 3, C_TEXT, false);
            final String t = targets[i];
            settingRects.add(new int[]{bx, sy, 62, 14});
            settingActions.add(() -> am.setSetting("Target", t));
            bx += 68;
        }

        sy += 20;
        // Smoothing preset buttons
        double curSmooth = parseDouble(am.getSetting("Smoothing"), 0.15);
        ctx.drawText(textRenderer, Text.literal("§7Smooth: §f" + String.format("%.2f", curSmooth)),
            sx + 8, sy, C_TEXT, false);
        sy += 12;
        float[] smoothVals = {0.05f, 0.10f, 0.20f, 0.40f, 1.00f};
        String[] smoothLabels = {"Silky", "Slow", "Med", "Fast", "Snap"};
        bx = sx + 8;
        for (int i = 0; i < smoothVals.length; i++) {
            boolean sel = Math.abs((float) curSmooth - smoothVals[i]) < 0.01f;
            boolean hov = inRect(mx, my, bx, sy, 40, 14);
            ctx.fill(bx, sy, bx + 40, sy + 14, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            drawBorder(ctx, bx, sy, 40, 14, sel ? C_ACCENT : C_DIVIDER);
            ctx.drawText(textRenderer, Text.literal(smoothLabels[i]), bx + 4, sy + 3, C_TEXT, false);
            final String sv = String.valueOf(smoothVals[i]);
            settingRects.add(new int[]{bx, sy, 40, 14});
            settingActions.add(() -> am.setSetting("Smoothing", sv));
            bx += 46;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mouse events
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int x = (int) mx, y = (int) my;

        // Close button
        int cx = px + PANEL_W - 20, cy = py + 5;
        if (inRect(x, y, cx, cy, 16, 16)) { close(); return true; }

        // Category sidebar
        for (int[] r : categoryRects) {
            if (inRect(x, y, r[0], r[1], r[2], r[3])) {
                selectedCategory = categories.get(r[4]);
                selectedModule = null;
                return true;
            }
        }

        // Setting buttons
        for (int i = 0; i < settingRects.size(); i++) {
            int[] r = settingRects.get(i);
            if (inRect(x, y, r[0], r[1], r[2], r[3])) {
                settingActions.get(i).run();
                return true;
            }
        }

        // Module cards — toggle button or select
        List<Module> mods = byCategory.getOrDefault(selectedCategory, List.of());
        int gx = px + SIDEBAR_W + 10;
        int gy = py + HEADER_H + 10;
        int gridW = PANEL_W - SIDEBAR_W - 20;
        int settingsH = (selectedModule != null) ? SETTINGS_H + 10 : 0;
        int cols = Math.max(1, (gridW + CARD_PAD) / (CARD_W + CARD_PAD));

        for (int idx = 0; idx < mods.size(); idx++) {
            Module m = mods.get(idx);
            int col = idx % cols, row = idx / cols;
            int cardX = gx + col * (CARD_W + CARD_PAD);
            int cardY = gy + row * (CARD_H + CARD_PAD);

            if (!inRect(x, y, cardX, cardY, CARD_W, CARD_H)) continue;

            // Toggle button area
            int bx = cardX + CARD_W - 34, by = cardY + CARD_H - 18;
            if (inRect(x, y, bx, by, 28, 12)) {
                m.toggle();
            } else {
                selectedModule = (m == selectedModule) ? null : m;
            }
            return true;
        }

        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x,         y,         x + w, y + 1,     color); // top
        ctx.fill(x,         y + h - 1, x + w, y + h,     color); // bottom
        ctx.fill(x,         y,         x + 1, y + h,     color); // left
        ctx.fill(x + w - 1, y,         x + w, y + h,     color); // right
    }

    private float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}
