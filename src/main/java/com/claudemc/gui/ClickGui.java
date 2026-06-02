package com.claudemc.gui;

import com.claudemc.ClaudeMCClient;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.gui.AISettingsScreen;
import com.claudemc.gui.AltScreen;
import com.claudemc.gui.BlockESPScreen;
import com.claudemc.gui.MacroScreen;
import com.claudemc.gui.ServerInfoScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;

import com.claudemc.module.setting.StringSetting;
import java.util.*;

/**
 * Meteor Client-inspired ClickGUI.
 *
 *  Layout: one draggable panel per Category.
 *  Left-click module name  → toggle on/off
 *  Right-click module name → expand settings inline
 *  Drag panel header       → move panel
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

    // ── Panel state ───────────────────────────────────────────────────────
    private static final Map<Category, int[]> panelPos = new LinkedHashMap<>();

    // Row 1 categories (y=32): COMBAT, MOVEMENT, PLAYER, RENDER, WORLD
    // Row 2 categories (y=260): EXPLOIT, CHAT, UTILITY, MISC
    static {
        Category[] row1 = {Category.COMBAT, Category.MOVEMENT, Category.PLAYER, Category.RENDER, Category.WORLD};
        Category[] row2 = {Category.EXPLOIT, Category.CHAT, Category.UTILITY, Category.MISC};
        int x = 10;
        for (Category cat : row1) { panelPos.put(cat, new int[]{x, 32}); x += 130; }
        x = 10;
        for (Category cat : row2) { panelPos.put(cat, new int[]{x, 260}); x += 130; }
    }

    private static final Map<Category, Boolean> collapsed = new EnumMap<>(Category.class);
    private static final Set<Module>  expanded  = new HashSet<>();   // settings expanded

    private Category     dragging      = null;
    private int          dragOffX      = 0;
    private int          dragOffY      = 0;

    // ── Inline string editor state ────────────────────────────────────────
    private StringSetting editingSetting = null;
    private String        editBuffer     = "";

    // ── Search bar ────────────────────────────────────────────────────────
    private String searchQuery = "";

    // ── Tooltip popup (middle-click module description) ───────────────────
    private String tooltipText = null;
    private int    tooltipX, tooltipY;

    // ── Slider drag state ─────────────────────────────────────────────────
    private com.claudemc.module.setting.NumberSetting draggingSlider = null;
    private int sliderPanelX, sliderWidth;

    public ClickGui() {
        super(Text.literal("ClaudeMC"));
    }

    @Override public boolean shouldPause() { return false; }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // dim world
        ctx.fill(0, 0, width, height, 0x55000000);

        // ── Search bar ────────────────────────────────────────────────────
        ctx.fill(0, 0, width, 18, 0xFF18181F);
        ctx.fill(4, 2, width - 4, 16, 0xFF111117);
        String searchDisplay = searchQuery.isEmpty() ? "§8Search modules…" : "§f" + searchQuery;
        ctx.drawText(textRenderer, Text.literal(searchDisplay), 8, 5, 0xFFFFFF, false);
        if (!searchQuery.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx2 = 8 + textRenderer.getWidth(searchQuery);
            ctx.fill(cx2, 4, cx2 + 1, 14, 0xFFAAAAAA);
        }

        for (Category cat : Category.values()) {
            drawPanel(ctx, mx, my, cat);
        }

        // ── Tooltip popup ─────────────────────────────────────────────────
        if (tooltipText != null) {
            int tw = textRenderer.getWidth(tooltipText.replaceAll("§.", ""));
            int tx = Math.min(tooltipX, width - tw - 10);
            int ty = Math.max(0, tooltipY - 20);
            ctx.fill(tx - 2, ty - 2, tx + tw + 6, ty + 12, 0xDD000000);
            ctx.fill(tx - 2, ty - 2, tx - 1, ty + 12, 0xFFFFAA44);
            ctx.drawText(textRenderer, Text.literal("§7" + tooltipText), tx + 2, ty + 2, 0xFFFFFF, false);
        }

        // Footer bar
        ctx.fill(0, height - 16, width, height, 0xFF18181F);
        ctx.drawText(textRenderer, Text.literal(
                "§7L=toggle §8| §7R=settings §8| §7Mid=info §8| §7Drag#=slider §8| §7Scroll=step"),
            4, height - 11, 0x888888, false);
        // Buttons right-to-left: [Keybinds] [Macros] [Alts] [Server Info]
        int bY = height - 13, bH = 11;
        int bX = width - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Keybinds]",   0xFF4ADE80) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Macros]",     0xFFFFAA44) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Alts]",       0xFF44AAFF) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[Server Info]",0xFF44AAFF) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[BlockESP]",   0xFFFF88FF) - 4;
        bX = drawFooterBtn(ctx, bX, bY, bH, "§f[AI]",         0xFF88FFFF) - 4;
    }

    private void drawPanel(DrawContext ctx, int mx, int my, Category cat) {
        int[] pos  = panelPos.get(cat);
        if (pos == null) return;
        int px = pos[0], py = pos[1];
        int pw = 124;

        List<Module> allMods = new ArrayList<>(ClaudeMCClient.MODULES.getByCategory(cat));
        // Sort alphabetically within category
        allMods.sort(Comparator.comparing(Module::getName));
        // Filter by search query
        List<Module> mods = searchQuery.isEmpty() ? allMods
            : allMods.stream().filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase())).toList();
        boolean col = collapsed.getOrDefault(cat, false);

        // Calculate panel height
        int ph = 14;
        if (!col) {
            for (Module m : mods) {
                ph += 12;
                if (expanded.contains(m)) ph += m.getSettings().size() * 12 + 4;
            }
            ph += 2;
        }

        // Panel BG
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 14, C_HEADER);
        // Left accent
        ctx.fill(px, py, px + 2, py + ph, cat.color);
        // Header text
        ctx.drawText(textRenderer, Text.literal(cat.displayName), px + 6, py + 3, cat.color, true);
        // Collapse indicator
        ctx.drawText(textRenderer, Text.literal(col ? "▶" : "▼"), px + pw - 14, py + 3, C_SUB, false);

        if (col) return;

        int my_ = py + 14;
        for (Module m : mods) {
            boolean hover = inRect(mx, my, px + 2, my_, pw - 4, 12);
            ctx.fill(px + 2, my_, px + pw - 2, my_ + 12, hover ? C_MOD_HOV : C_MOD_BG);

            // Enabled dot
            int dotCol = m.isEnabled() ? C_ENABLED : C_DISABLED;
            ctx.fill(px + 4, my_ + 4, px + 8, my_ + 8, dotCol);

            // Name
            String name = m.getName();
            int bind = KeybindManager.INSTANCE.getModuleBind(m.getName());
            String bindHint = (bind != -1) ? " §8[" + KeybindManager.keyName(bind) + "]" : "";
            int maxNameW = pw - 20 - textRenderer.getWidth(bindHint.replaceAll("§.", ""));
            if (textRenderer.getWidth(name) > maxNameW) {
                name = textRenderer.trimToWidth(name, maxNameW) + "…";
            }
            ctx.drawText(textRenderer, Text.literal((m.isEnabled() ? "§f" : "§7") + name + bindHint),
                px + 10, my_ + 2, C_TEXT, false);

            my_ += 12;

            // Settings (expanded)
            if (expanded.contains(m)) {
                for (com.claudemc.module.setting.Setting s : m.getSettings()) {
                    boolean rowHover = inRect(mx, my, px + 4, my_, pw - 6, 12);
                    boolean isEditing = (s == editingSetting);
                    int rowBg = isEditing ? 0xFF1A1A2E : (rowHover ? 0xFF15151F : 0xFF0D0D14);
                    ctx.fill(px + 4, my_, px + pw - 2, my_ + 12, rowBg);

                    if (s instanceof com.claudemc.module.setting.NumberSetting ns) {
                        // Draw slider bar
                        int slotX = px + 4, slotW = pw - 6;
                        double frac = ns.getFraction();
                        int fillW = (int)(frac * (slotW - 2));
                        ctx.fill(slotX + 1, my_ + 8, slotX + 1 + fillW, my_ + 11, 0xFF4E6EF2);
                        ctx.fill(slotX + 1 + fillW, my_ + 8, slotX + slotW - 1, my_ + 11, 0xFF333344);
                        if (isEditing) {
                            String buf = editBuffer;
                            String raw = buf + "|";
                            ctx.drawText(textRenderer,
                                Text.literal("§8 " + s.getName() + ": §e" + raw),
                                px + 6, my_ + 1, C_SUB, false);
                        } else {
                            ctx.drawText(textRenderer,
                                Text.literal("§8 " + s.getName() + ": §a" + ns.asString()),
                                px + 6, my_ + 1, C_SUB, false);
                        }
                    } else {
                        String val;
                        if (isEditing) {
                            String buf = editBuffer;
                            int maxW = pw - 16 - textRenderer.getWidth(s.getName() + ": ");
                            String raw = buf + "|";
                            if (textRenderer.getWidth(raw) > maxW && buf.length() > 0) {
                                raw = textRenderer.trimToWidth(new StringBuilder(raw).reverse().toString(), maxW);
                                raw = new StringBuilder(raw).reverse().toString();
                            }
                            val = "§e" + raw;
                        } else {
                            val = (s.isEditable() ? "§a" : "§7") + s.asString();
                        }
                        ctx.drawText(textRenderer,
                            Text.literal("§8 " + s.getName() + ": " + val),
                            px + 6, my_ + 2, C_SUB, false);
                    }
                    my_ += 12;
                }
                my_ += 4;
            }
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────

    /** Commit any in-progress string edit. */
    private void commitEdit() {
        if (editingSetting != null) {
            editingSetting.set(editBuffer);
            com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
            editingSetting = null;
        }
    }

    @Override
    public void removed() {
        commitEdit();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(); double my = click.y(); int button = click.button();
        int x = (int) mx, y = (int) my;
        // Commit any active string edit when clicking elsewhere
        if (editingSetting != null) commitEdit();

        // Footer buttons — mirror render order (right-to-left) to determine hit areas
        if (button == 0 && y >= height - 13 && y < height - 2) {
            assert client != null;
            // Compute button positions same as render: right-to-left
            int bX = width - 4;
            // [Keybinds]
            int kbW  = textRenderer.getWidth("[Keybinds]")  + 10; bX -= kbW;
            if (x >= bX && x < bX + kbW) { client.setScreen(new KeybindScreen());   return true; }
            bX -= 4;
            // [Macros]
            int macW = textRenderer.getWidth("[Macros]")    + 10; bX -= macW;
            if (x >= bX && x < bX + macW) { client.setScreen(new MacroScreen());    return true; }
            bX -= 4;
            // [Alts]
            int altW = textRenderer.getWidth("[Alts]")      + 10; bX -= altW;
            if (x >= bX && x < bX + altW) { client.setScreen(new AltScreen());      return true; }
            bX -= 4;
            // [Server Info]
            int siW  = textRenderer.getWidth("[Server Info]")+ 10; bX -= siW;
            if (x >= bX && x < bX + siW)  { client.setScreen(new ServerInfoScreen()); return true; }
            bX -= 4;
            // [BlockESP]
            int beW  = textRenderer.getWidth("[BlockESP]")  + 10; bX -= beW;
            if (x >= bX && x < bX + beW)  { client.setScreen(new BlockESPScreen());   return true; }
            bX -= 4;
            // [AI]
            int aiW  = textRenderer.getWidth("[AI]")        + 10; bX -= aiW;
            if (x >= bX && x < bX + aiW)  { client.setScreen(new AISettingsScreen()); return true; }
        }

        for (Category cat : Category.values()) {
            int[] pos = panelPos.get(cat);
            if (pos == null) continue;
            int px = pos[0], py = pos[1], pw = 124;

            // Header click: left-click collapses/expands; either button starts drag
            if (inRect(x, y, px, py, pw, 14)) {
                if (button == 0) {
                    collapsed.merge(cat, false, (a, b) -> !a);
                }
                dragging = cat;
                dragOffX = x - px;
                dragOffY = y - py;
                return true;
            }

            if (collapsed.getOrDefault(cat, false)) continue;

            // Get same filtered+sorted list as render
            List<Module> allMods = new ArrayList<>(ClaudeMCClient.MODULES.getByCategory(cat));
            allMods.sort(Comparator.comparing(Module::getName));
            List<Module> mods = searchQuery.isEmpty() ? allMods
                : allMods.stream().filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase())).toList();

            int my_ = py + 14;
            for (Module m : mods) {
                if (inRect(x, y, px + 2, my_, pw - 4, 12)) {
                    if (button == 0) m.toggle();
                    else if (button == 1) {
                        if (expanded.contains(m)) expanded.remove(m);
                        else expanded.add(m);
                    } else if (button == 2) {
                        // Middle-click: show description tooltip
                        tooltipText = m.getDescription();
                        tooltipX = x; tooltipY = y;
                    }
                    return true;
                }
                my_ += 12;
                // Setting rows (only when expanded)
                if (expanded.contains(m)) {
                    for (com.claudemc.module.setting.Setting s : m.getSettings()) {
                        if (inRect(x, y, px + 4, my_, pw - 6, 12)) {
                            if (s instanceof com.claudemc.module.setting.NumberSetting ns) {
                                if (button == 0) {
                                    // Start slider drag
                                    draggingSlider = ns;
                                    sliderPanelX = px + 5;
                                    sliderWidth = pw - 8;
                                    double frac = Math.max(0, Math.min(1.0, (double)(x - sliderPanelX) / sliderWidth));
                                    ns.setFraction(frac);
                                    com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
                                } else if (button == 1) {
                                    // Right-click: open text edit for number setting
                                    editBuffer = ns.asString();
                                    editingSetting = new StringSetting(s.getName(), ns.asString()) {
                                        @Override public String get() { return ns.asString(); }
                                        @Override public void set(String v) { ns.fromString(v); com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES); }
                                    };
                                }
                            } else if (s instanceof StringSetting ss) {
                                if (button == 0) {
                                    editingSetting = ss;
                                    editBuffer = ss.get();
                                } else if (button == 1) {
                                    ss.set("");
                                    com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
                                }
                            } else if (s.isEditable()) {
                                if (button == 0)      s.onLeftClick();
                                else if (button == 1) s.onRightClick();
                                com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
                            }
                            return true;
                        }
                        my_ += 12;
                    }
                    my_ += 4;
                }
            }
        }
        return false;
    }

    // ── Keyboard (string editor + search bar) ────────────────────────────

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(); int scanCode = input.scancode(); int modifiers = input.modifiers();

        // Close GUI with the same key that opens it
        if (keyCode == KeybindManager.INSTANCE.getGuiKey()) {
            close();
            return true;
        }

        if (editingSetting != null) {
            switch (keyCode) {
                case 257, 335 -> { // ENTER / numpad enter → commit
                    commitEdit();
                }
                case 256 -> {      // ESC → cancel
                    editingSetting = null;
                }
                case 259 -> {      // BACKSPACE
                    if (!editBuffer.isEmpty())
                        editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                }
            }
            return true;          // consume all keys while editing
        }

        // Search bar backspace
        if (keyCode == 259 && !searchQuery.isEmpty()) { // BACKSPACE
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        // ESC clears search or closes
        if (keyCode == 256) {
            if (!searchQuery.isEmpty()) { searchQuery = ""; return true; }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint(); int modifiers = input.modifiers();
        if (editingSetting != null) {
            if (chr >= 32) editBuffer += chr;
            return true;
        }
        // Type into search bar
        if (chr >= 32) {
            searchQuery += chr;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x(); double my = click.y(); int button = click.button();
        if (draggingSlider != null) {
            double frac = Math.max(0, Math.min(1.0, (mx - sliderPanelX) / sliderWidth));
            draggingSlider.setFraction(frac);
            com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
            return true;
        }
        if (dragging != null) {
            int[] pos = panelPos.get(dragging);
            pos[0] = (int) mx - dragOffX;
            pos[1] = (int) my - dragOffY;
            // Clamp to screen
            pos[0] = Math.max(0, Math.min(width  - 124, pos[0]));
            pos[1] = Math.max(0, Math.min(height - 40,  pos[1]));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x(); double my = click.y(); int button = click.button();
        if (draggingSlider != null) { draggingSlider = null; return true; }
        dragging = null;
        // Clear tooltip on any mouse release
        tooltipText = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        // Scroll over a number setting row to step it
        int x = (int) mx, y = (int) my;
        for (Category cat : Category.values()) {
            int[] pos = panelPos.get(cat);
            if (pos == null) continue;
            int px = pos[0], py = pos[1], pw = 124;
            if (collapsed.getOrDefault(cat, false)) continue;
            List<Module> allMods = new ArrayList<>(ClaudeMCClient.MODULES.getByCategory(cat));
            allMods.sort(Comparator.comparing(Module::getName));
            List<Module> mods = searchQuery.isEmpty() ? allMods
                : allMods.stream().filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase())).toList();
            int my_ = py + 14;
            for (Module m : mods) {
                my_ += 12;
                if (expanded.contains(m)) {
                    for (com.claudemc.module.setting.Setting s : m.getSettings()) {
                        if (inRect(x, y, px + 4, my_, pw - 6, 12) && s instanceof com.claudemc.module.setting.NumberSetting ns) {
                            if (vScroll > 0) ns.onLeftClick(); else ns.onRightClick();
                            com.claudemc.config.ModuleConfig.save(ClaudeMCClient.MODULES);
                            return true;
                        }
                        my_ += 12;
                    }
                    my_ += 4;
                }
            }
        }
        return false;
    }

    /** Draws a right-aligned footer button and returns the new right edge X. */
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
