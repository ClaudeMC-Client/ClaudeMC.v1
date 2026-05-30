package com.claudemc.gui;

import com.claudemc.ClaudeMCClient;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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

    static {
        int x = 10;
        for (Category cat : Category.values()) {
            panelPos.put(cat, new int[]{x, 20});
            x += 130;
        }
    }

    private static final Map<Category, Boolean> collapsed = new EnumMap<>(Category.class);
    private static final Set<Module>  expanded  = new HashSet<>();   // settings expanded

    private Category dragging      = null;
    private int      dragOffX      = 0;
    private int      dragOffY      = 0;

    public ClickGui() {
        super(Text.literal("ClaudeMC"));
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // dim world
        ctx.fill(0, 0, width, height, 0x55000000);

        for (Category cat : Category.values()) {
            drawPanel(ctx, mx, my, cat);
        }
        // Footer bar
        ctx.fill(0, height - 14, width, height, 0xFF18181F);
        ctx.drawText(textRenderer, Text.literal("§7[" +
                KeybindManager.keyName(KeybindManager.INSTANCE.getGuiKey()) +
                "] close  §8|  §7LClick=toggle  RClick=settings"),
            4, height - 10, 0x888888, false);
        // [Server Info] button
        int siBtnW = 84, siBtnH = 12;
        int siBtnX = width - siBtnW - 82, siBtnY = height - 13;
        ctx.fill(siBtnX, siBtnY, siBtnX + siBtnW, siBtnY + siBtnH, 0xFF1C1C28);
        ctx.fill(siBtnX, siBtnY, siBtnX + 2, siBtnY + siBtnH, 0xFF44AAFF);
        ctx.drawText(textRenderer, Text.literal("§f[Server Info]"), siBtnX + 5, siBtnY + 2, 0xFFEEEEEE, false);
        // [Keybinds] button
        int btnW = 72, btnH = 12;
        int btnX = width - btnW - 4, btnY = height - 13;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFF1C1C28);
        ctx.fill(btnX, btnY, btnX + 2,   btnY + btnH, 0xFF4ADE80);
        ctx.drawText(textRenderer, Text.literal("§f[Keybinds]"), btnX + 5, btnY + 2, 0xFFEEEEEE, false);
    }

    private void drawPanel(DrawContext ctx, int mx, int my, Category cat) {
        int[] pos  = panelPos.get(cat);
        int px = pos[0], py = pos[1];
        int pw = 124;

        List<Module> mods = ClaudeMCClient.MODULES.getByCategory(cat);
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
                for (String[] s : m.getSettings()) {
                    ctx.fill(px + 4, my_, px + pw - 2, my_ + 12, 0xFF0D0D14);
                    ctx.drawText(textRenderer,
                        Text.literal("§8 " + s[0] + ": §7" + s[1]),
                        px + 6, my_ + 2, C_SUB, false);
                    my_ += 12;
                }
                my_ += 4;
            }
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = (int) mx, y = (int) my;

        // Server Info button
        int siBtnW = 84, siBtnX = width - siBtnW - 82, siBtnY = height - 13;
        if (button == 0 && inRect(x, y, siBtnX, siBtnY, siBtnW, 12)) {
            assert client != null;
            client.setScreen(new ServerInfoScreen());
            return true;
        }
        // Keybinds button
        int btnW = 72, btnX = width - btnW - 4, btnY = height - 13;
        if (button == 0 && inRect(x, y, btnX, btnY, btnW, 12)) {
            assert client != null;
            client.setScreen(new KeybindScreen());
            return true;
        }

        for (Category cat : Category.values()) {
            int[] pos = panelPos.get(cat);
            int px = pos[0], py = pos[1], pw = 124;

            // Header click
            if (inRect(x, y, px, py, pw, 14)) {
                if (button == 0) {
                    collapsed.merge(cat, false, (a, b) -> !a);
                } else if (button == 0) { /* reserved */ }
                dragging = cat;
                dragOffX = x - px;
                dragOffY = y - py;
                return true;
            }

            if (collapsed.getOrDefault(cat, false)) continue;

            int my_ = py + 14;
            for (Module m : ClaudeMCClient.MODULES.getByCategory(cat)) {
                if (inRect(x, y, px + 2, my_, pw - 4, 12)) {
                    if (button == 0) m.toggle();
                    else if (button == 1) {
                        if (expanded.contains(m)) expanded.remove(m);
                        else expanded.add(m);
                    }
                    return true;
                }
                my_ += 12;
                if (expanded.contains(m)) my_ += m.getSettings().size() * 12 + 4;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
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
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        return false;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
