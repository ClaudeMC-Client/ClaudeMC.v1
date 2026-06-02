package com.claudemc.gui;

import com.claudemc.ClaudeMCClient;
import com.claudemc.companion.CompanionServer;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.NumberSetting;
import com.claudemc.module.setting.Setting;
import com.claudemc.module.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Meteor Client-inspired ClickGUI.
 *
 *  Layout: one draggable panel per Category, plus a top search bar.
 *  Left-click module name   → toggle on/off
 *  Right-click module name   → expand settings inline
 *  Drag panel header         → move panel
 *  Number setting: drag row = slider, right-click = type a value, scroll = step
 */
public class ClickGui extends Screen {

    // ── Colours ──────────────────────────────────────────────────────────
    private static final int C_BG       = 0xE5101018;
    private static final int C_HEADER   = 0xFF18181F;
    private static final int C_MOD_BG   = 0xFF111117;
    private static final int C_MOD_HOV  = 0xFF1C1C28;
    private static final int C_ENABLED  = 0xFF4ADE80;
    private static final int C_DISABLED = 0xFF4B5563;
    private static final int C_TEXT     = 0xFFEEEEEE;
    private static final int C_SUB      = 0xFF9CA3AF;
    private static final int PW         = 124;

    // ── Panel state (shared across opens) ─────────────────────────────────
    private static final Map<Category, int[]> panelPos = new LinkedHashMap<>();
    static {
        // Row 1: core categories
        int x = 10;
        for (Category cat : new Category[]{Category.COMBAT, Category.MOVEMENT, Category.PLAYER, Category.RENDER, Category.WORLD}) {
            panelPos.put(cat, new int[]{x, 32});
            x += 130;
        }
        // Row 2: misc categories
        x = 10;
        for (Category cat : new Category[]{Category.EXPLOIT, Category.CHAT, Category.UTILITY, Category.MISC}) {
            panelPos.put(cat, new int[]{x, 260});
            x += 130;
        }
    }
    private static final Map<Category, Boolean> collapsed = new EnumMap<>(Category.class);
    private static final Set<Module>  expanded = new HashSet<>();

    // Pseudo-category key for the search-results panel.
    private static final int[] searchPanelPos = {10, 32};

    private Category dragging = null;
    private boolean  draggingSearch = false;
    private int      dragOffX = 0, dragOffY = 0;

    // ── Inline value editor (string + number typing) ──────────────────────
    private Setting editing    = null;
    private String  editBuffer = "";

    // ── Number slider drag ────────────────────────────────────────────────
    private NumberSetting draggingSlider = null;
    private int sliderTrackX = 0, sliderTrackW = 1;

    // ── Search ────────────────────────────────────────────────────────────
    private String searchQuery = "";

    // ── Description tooltip (middle-click) ────────────────────────────────
    private String tooltipTitle = null;
    private String tooltipDesc  = null;
    private int    tooltipX, tooltipY;

    public ClickGui() { super(Text.literal("ClaudeMC")); }

    @Override
    public boolean shouldPause() { return false; }   // never freeze the (singleplayer) world

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x55000000);

        drawSearchBar(ctx);

        if (searchQuery.isBlank()) {
            for (Category cat : Category.values()) drawPanel(ctx, mx, my, cat);
        } else {
            drawSearchResults(ctx, mx, my);
        }

        // Tooltip (middle-click module description)
        if (tooltipTitle != null) drawTooltip(ctx);

        // Footer
        ctx.fill(0, height - 16, width, height, 0xFF18181F);
        ctx.drawText(textRenderer, Text.literal("§7[" +
                KeybindManager.keyName(KeybindManager.INSTANCE.getGuiKey()) +
                "] close  §8|  §7L=toggle  R=settings  Mid=info  Drag#=slider  Scroll=step"),
            4, height - 11, 0x888888, false);
        int bY = height - 13, bH = 11, bX = width - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Keybinds]",   0xFF4ADE80) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Macros]",     0xFFFFAA44) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Alts]",       0xFF44AAFF) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Server Info]",0xFF44AAFF) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[BlockESP]",   0xFFFF88FF) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Companion]",  0xFF88FF88) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[AI]",         0xFF88FFFF) - 4;
    }

    private void drawSearchBar(DrawContext ctx) {
        int sx = 10, sy = 8, sw = 200, sh = 16;
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xCC0D0D14);
        ctx.fill(sx, sy, sx + 2, sy + sh, 0xFF4E6EF2);
        String shown = searchQuery.isBlank()
            ? "§7Search modules…"
            : "§f" + searchQuery + "§a|";
        ctx.drawText(textRenderer, Text.literal("§7🔎 " + shown), sx + 8, sy + 4, 0xFFFFFF, false);
    }

    /** Modules sorted alphabetically within a category. */
    private List<Module> sortedModules(Category cat) {
        return ClaudeMCClient.MODULES.getByCategory(cat).stream()
            .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    private List<Module> searchMatches() {
        String q = searchQuery.toLowerCase(Locale.ROOT);
        return ClaudeMCClient.MODULES.getModules().stream()
            .filter(m -> m.getName().toLowerCase(Locale.ROOT).contains(q))
            .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    private void drawSearchResults(DrawContext ctx, int mx, int my) {
        int px = searchPanelPos[0], py = searchPanelPos[1];
        List<Module> mods = searchMatches();
        int ph = 14 + 2;
        for (Module m : mods) { ph += 12; if (expanded.contains(m)) ph += m.getSettings().size() * 14 + 4; }

        ctx.fill(px, py, px + PW, py + ph, C_BG);
        ctx.fill(px, py, px + PW, py + 14, C_HEADER);
        ctx.fill(px, py, px + 2, py + ph, 0xFF4E6EF2);
        ctx.drawText(textRenderer, Text.literal("Results (" + mods.size() + ")"), px + 6, py + 3, 0xFF8FB4FF, true);

        int yy = py + 14;
        for (Module m : mods) yy = drawModuleRow(ctx, mx, my, px, yy, m);
    }

    private void drawPanel(DrawContext ctx, int mx, int my, Category cat) {
        int[] pos = panelPos.get(cat);
        int px = pos[0], py = pos[1];
        List<Module> mods = sortedModules(cat);
        boolean col = collapsed.getOrDefault(cat, false);

        int ph = 14;
        if (!col) {
            for (Module m : mods) {
                ph += 12;
                if (expanded.contains(m)) ph += m.getSettings().size() * 14 + 4;
            }
            ph += 2;
        }

        ctx.fill(px, py, px + PW, py + ph, C_BG);
        ctx.fill(px, py, px + PW, py + 14, C_HEADER);
        ctx.fill(px, py, px + 2, py + ph, cat.color);
        ctx.drawText(textRenderer, Text.literal(cat.displayName), px + 6, py + 3, cat.color, true);
        ctx.drawText(textRenderer, Text.literal(col ? "▶" : "▼"), px + PW - 14, py + 3, C_SUB, false);

        if (col) return;
        int yy = py + 14;
        for (Module m : mods) yy = drawModuleRow(ctx, mx, my, px, yy, m);
    }

    /** Draws one module row + its expanded settings; returns the new y cursor. */
    private int drawModuleRow(DrawContext ctx, int mx, int my, int px, int yy, Module m) {
        boolean hover = inRect(mx, my, px + 2, yy, PW - 4, 12);
        ctx.fill(px + 2, yy, px + PW - 2, yy + 12, hover ? C_MOD_HOV : C_MOD_BG);
        ctx.fill(px + 4, yy + 4, px + 8, yy + 8, m.isEnabled() ? C_ENABLED : C_DISABLED);

        String name = m.getName();
        int bind = KeybindManager.INSTANCE.getModuleBind(m.getName());
        String bindHint = (bind != -1) ? " §8[" + KeybindManager.keyName(bind) + "]" : "";
        int maxNameW = PW - 20 - textRenderer.getWidth(bindHint.replaceAll("§.", ""));
        if (textRenderer.getWidth(name) > maxNameW) name = textRenderer.trimToWidth(name, maxNameW) + "…";
        ctx.drawText(textRenderer, Text.literal((m.isEnabled() ? "§f" : "§7") + name + bindHint),
            px + 10, yy + 2, C_TEXT, false);
        yy += 12;

        if (expanded.contains(m)) {
            for (Setting s : m.getSettings()) yy = drawSettingRow(ctx, mx, my, px, yy, s);
            yy += 4;
        }
        return yy;
    }

    private int drawSettingRow(DrawContext ctx, int mx, int my, int px, int yy, Setting s) {
        int rowH = 14;
        boolean rowHover = inRect(mx, my, px + 4, yy, PW - 6, rowH);
        boolean isEditing = (s == editing);
        int rowBg = isEditing ? 0xFF1A1A2E : (rowHover ? 0xFF15151F : 0xFF0D0D14);
        ctx.fill(px + 4, yy, px + PW - 2, yy + rowH, rowBg);

        if (s instanceof NumberSetting ns && !isEditing) {
            // slider track with filled portion + value text
            int trackX = px + 6, trackW = PW - 14;
            int trackY = yy + 10;
            ctx.fill(trackX, trackY, trackX + trackW, trackY + 2, 0xFF2A2A38);
            int fill = (int) (trackW * ns.getFraction());
            ctx.fill(trackX, trackY, trackX + fill, trackY + 2, 0xFF4ADE80);
            ctx.drawText(textRenderer, Text.literal("§8" + s.getName() + ": §a" + s.asString()),
                px + 6, yy + 1, C_SUB, false);
        } else {
            String val;
            if (isEditing) {
                String raw = editBuffer + "|";
                int maxW = PW - 16 - textRenderer.getWidth(s.getName() + ": ");
                if (textRenderer.getWidth(raw) > maxW && editBuffer.length() > 0) {
                    raw = new StringBuilder(textRenderer.trimToWidth(
                        new StringBuilder(raw).reverse().toString(), maxW)).reverse().toString();
                }
                val = "§e" + raw;
            } else {
                val = (s.isEditable() ? "§a" : "§7") + s.asString();
            }
            ctx.drawText(textRenderer, Text.literal("§8" + s.getName() + ": " + val),
                px + 6, yy + 3, C_SUB, false);
        }
        return yy + rowH;
    }

    // ── Edit commit ───────────────────────────────────────────────────────

    private void commitEdit() {
        if (editing != null) {
            editing.fromString(editBuffer);
            com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
            editing = null;
        }
    }

    @Override public void removed() { commitEdit(); }

    // ── Mouse ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int x = (int) mxd, y = (int) myd;
        if (button != 2) tooltipTitle = null;  // any non-middle-click dismisses the tooltip
        if (editing != null) commitEdit();

        // Search bar focus / clear
        if (inRect(x, y, 10, 8, 200, 16)) {
            if (button == 1) searchQuery = "";   // right-click clears
            draggingSearch = false;
            return true;
        }

        // Footer buttons
        if (button == 0 && y >= height - 13 && y < height - 2) {
            assert client != null;
            int bX = width - 4;
            int kbW  = textRenderer.getWidth("[Keybinds]")   + 10; bX -= kbW;
            if (x >= bX && x < bX + kbW) { client.setScreen(new KeybindScreen());     return true; } bX -= 4;
            int macW = textRenderer.getWidth("[Macros]")     + 10; bX -= macW;
            if (x >= bX && x < bX + macW){ client.setScreen(new MacroScreen());       return true; } bX -= 4;
            int altW = textRenderer.getWidth("[Alts]")       + 10; bX -= altW;
            if (x >= bX && x < bX + altW){ client.setScreen(new AltScreen());         return true; } bX -= 4;
            int siW  = textRenderer.getWidth("[Server Info]")+ 10; bX -= siW;
            if (x >= bX && x < bX + siW) { client.setScreen(new ServerInfoScreen());  return true; } bX -= 4;
            int beW  = textRenderer.getWidth("[BlockESP]")   + 10; bX -= beW;
            if (x >= bX && x < bX + beW) { client.setScreen(new BlockESPScreen());    return true; } bX -= 4;
            int coW  = textRenderer.getWidth("[Companion]")  + 10; bX -= coW;
            if (x >= bX && x < bX + coW) { openCompanion();                            return true; } bX -= 4;
            int aiW  = textRenderer.getWidth("[AI]")         + 10; bX -= aiW;
            if (x >= bX && x < bX + aiW) { client.setScreen(new AISettingsScreen());  return true; }
        }

        if (!searchQuery.isBlank()) {
            return clickResults(x, y, button);
        }

        for (Category cat : Category.values()) {
            int[] pos = panelPos.get(cat);
            int px = pos[0], py = pos[1];
            if (inRect(x, y, px, py, PW, 14)) {
                if (button == 0) collapsed.merge(cat, false, (a, b) -> !a);
                dragging = cat; dragOffX = x - px; dragOffY = y - py;
                return true;
            }
            if (collapsed.getOrDefault(cat, false)) continue;
            int yy = py + 14;
            for (Module m : sortedModules(cat)) {
                Object r = clickModule(x, y, button, px, yy, m);
                if (r instanceof Boolean b) return b;
                yy = (int) r;
            }
        }
        return false;
    }

    private boolean clickResults(int x, int y, int button) {
        int px = searchPanelPos[0], py = searchPanelPos[1];
        if (inRect(x, y, px, py, PW, 14)) {
            draggingSearch = true; dragOffX = x - px; dragOffY = y - py; return true;
        }
        int yy = py + 14;
        for (Module m : searchMatches()) {
            Object r = clickModule(x, y, button, px, yy, m);
            if (r instanceof Boolean b) return b;
            yy = (int) r;
        }
        return false;
    }

    /** Returns Boolean if consumed, else the new y cursor (Integer). */
    private Object clickModule(int x, int y, int button, int px, int yy, Module m) {
        if (inRect(x, y, px + 2, yy, PW - 4, 12)) {
            if (button == 0) m.toggle();
            else if (button == 1) { if (!expanded.remove(m)) expanded.add(m); }
            else if (button == 2) {   // middle-click → show description tooltip
                tooltipTitle = m.getName();
                tooltipDesc  = m.getDescription();
                tooltipX     = x; tooltipY = y;
            }
            return Boolean.TRUE;
        }
        yy += 12;
        if (expanded.contains(m)) {
            for (Setting s : m.getSettings()) {
                if (inRect(x, y, px + 4, yy, PW - 6, 14)) {
                    handleSettingClick(s, button, px);
                    return Boolean.TRUE;
                }
                yy += 14;
            }
            yy += 4;
        }
        return yy;
    }

    private void handleSettingClick(Setting s, int button, int px) {
        if (s instanceof StringSetting ss) {
            if (button == 0) { editing = ss; editBuffer = ss.get(); }
            else { ss.set(""); save(); }
        } else if (s instanceof NumberSetting ns) {
            if (button == 1) {                       // right-click → type a value
                editing = ns; editBuffer = ns.asString();
            } else {                                  // left-click → start slider drag
                draggingSlider = ns;
                sliderTrackX = px + 6;
                sliderTrackW = PW - 14;
            }
        } else if (s.isEditable()) {
            if (button == 0) s.onLeftClick(); else s.onRightClick();
            save();
        }
    }

    @Override
    public boolean mouseDragged(double mxd, double myd, int button, double dx, double dy) {
        if (draggingSlider != null) {
            double f = (mxd - sliderTrackX) / Math.max(1, sliderTrackW);
            draggingSlider.setFraction(f);
            return true;
        }
        if (dragging != null) {
            int[] pos = panelPos.get(dragging);
            pos[0] = clampX((int) mxd - dragOffX);
            pos[1] = clampY((int) myd - dragOffY);
        } else if (draggingSearch) {
            searchPanelPos[0] = clampX((int) mxd - dragOffX);
            searchPanelPos[1] = clampY((int) myd - dragOffY);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSlider != null) { draggingSlider = null; save(); }
        dragging = null; draggingSearch = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        // Scroll over a NumberSetting row → step it.
        if (searchQuery.isBlank()) {
            for (Category cat : Category.values()) {
                if (collapsed.getOrDefault(cat, false)) continue;
                int[] pos = panelPos.get(cat);
                if (scrollPanel(mx, my, v, pos[0], pos[1], sortedModules(cat))) return true;
            }
        } else if (scrollPanel(mx, my, v, searchPanelPos[0], searchPanelPos[1], searchMatches())) {
            return true;
        }
        return false;
    }

    private boolean scrollPanel(double mx, double my, double v, int px, int py, List<Module> mods) {
        int yy = py + 14;
        for (Module m : mods) {
            yy += 12;
            if (expanded.contains(m)) {
                for (Setting s : m.getSettings()) {
                    if (s instanceof NumberSetting ns && inRect((int) mx, (int) my, px + 4, yy, PW - 6, 14)) {
                        if (v > 0) ns.onLeftClick(); else ns.onRightClick();
                        save();
                        return true;
                    }
                    yy += 14;
                }
                yy += 4;
            }
        }
        return false;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing != null) {
            switch (keyCode) {
                case 257, 335 -> commitEdit();          // ENTER → commit
                case 256      -> editing = null;          // ESC → cancel edit
                case 259      -> {                         // BACKSPACE
                    if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                }
            }
            return true;
        }
        // Close with the same key that opens the GUI.
        if (keyCode == KeybindManager.INSTANCE.getGuiKey()) { closeGui(); return true; }
        if (keyCode == 259) {                              // BACKSPACE edits the search box
            if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editing != null) {
            if (chr >= 32) editBuffer += chr;
            return true;
        }
        if (chr >= 32) { searchQuery += chr; return true; }   // type into the search box
        return super.charTyped(chr, modifiers);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────

    private void drawTooltip(DrawContext ctx) {
        if (tooltipTitle == null) return;
        String title = "§b" + tooltipTitle;
        String desc  = tooltipDesc != null && !tooltipDesc.isBlank() ? tooltipDesc : "§7No description.";
        // Word-wrap description at ~200 px
        List<String> lines = wrapText(desc, 200);
        int tw = Math.max(textRenderer.getWidth(title.replaceAll("§.", "")),
                          lines.stream().mapToInt(l -> textRenderer.getWidth(l.replaceAll("§.", ""))).max().orElse(0));
        int th = 10 + lines.size() * 10;
        int tx = Math.min(tooltipX + 8, width  - tw - 8);
        int ty = Math.min(tooltipY + 4, height - th - 20);
        ctx.fill(tx - 4, ty - 4, tx + tw + 4, ty + th + 4, 0xEE0D0D14);
        ctx.fill(tx - 4, ty - 4, tx - 2, ty + th + 4, 0xFF4ADE80);
        ctx.drawText(textRenderer, Text.literal(title), tx, ty, 0xFFFFFF, true);
        int ly = ty + 11;
        for (String line : lines) { ctx.drawText(textRenderer, Text.literal(line), tx, ly, 0xFFCCCCCC, false); ly += 10; }
    }

    private List<String> wrapText(String text, int maxPx) {
        List<String> out = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.isEmpty() ? w : line + " " + w;
            if (textRenderer.getWidth(test.replaceAll("§.", "")) > maxPx) {
                if (!line.isEmpty()) { out.add(line.toString()); line.setLength(0); }
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(w);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out.isEmpty() ? List.of(text) : out;
    }

    private void closeGui() { if (client != null) client.setScreen(null); }

    private void openCompanion() {
        CompanionServer.INSTANCE.open();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("§a[Companion] §7Open in browser: §f"
                + CompanionServer.INSTANCE.getUrl()), false);
        }
    }

    private void save() { com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES); }

    private int clampX(int x) { return Math.max(0, Math.min(width  - PW, x)); }
    private int clampY(int y) { return Math.max(0, Math.min(height - 20, y)); }

    private int drawFooterBtn(DrawContext ctx, int rightX, int y, int h, String label, int accent) {
        int w = textRenderer.getWidth(label.replaceAll("§.", "")) + 10;
        int x = rightX - w;
        ctx.fill(x, y, x + w, y + h, 0xFF1C1C28);
        ctx.fill(x, y, x + 2, y + h, accent);
        ctx.drawText(textRenderer, Text.literal(label), x + 4, y + 2, 0xFFEEEEEE, false);
        return x;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
