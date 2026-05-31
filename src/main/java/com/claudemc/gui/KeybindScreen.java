package com.claudemc.gui;

import com.claudemc.ClaudeMCClient;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen keybind editor.
 * Accessible via the [Keybinds] button in the ClickGUI.
 *
 * Layout:
 *   - First row: GUI Open Key (special)
 *   - Then one row per module, grouped and scrollable
 *
 * Click any row → enters listening mode (row flashes cyan).
 * Press any key  → binds it. Esc = cancel. Delete/Backspace = clear bind.
 */
public class KeybindScreen extends Screen {

    // ── Colours ──────────────────────────────────────────────────────────
    private static final int C_BG       = 0xE5101018;
    private static final int C_ROW      = 0xFF111117;
    private static final int C_HOV      = 0xFF1C1C28;
    private static final int C_LISTEN   = 0xFF0E3050;
    private static final int C_ACCENT   = 0xFF4ADE80;
    private static final int C_WARN     = 0xFFFFAA44;
    private static final int C_TEXT     = 0xFFEEEEEE;
    private static final int C_SUB      = 0xFF9CA3AF;
    private static final int C_HEADER   = 0xFF18181F;

    private static final int ROW_H  = 16;
    private static final int COL_W  = 280;
    private static final int COLS   = 2;
    private static final int PAD    = 6;

    // Row entries
    private record BindRow(String label, String moduleNameOrNull) {}
    private final List<BindRow> rows = new ArrayList<>();

    // Listening state: index into `rows`, or -1
    private int listeningRow = -1;

    private int scrollOffset = 0;

    public KeybindScreen() {
        super(Text.literal("ClaudeMC — Keybinds"));
    }

    @Override
    protected void init() {
        rows.clear();
        // GUI key row (moduleNameOrNull == null signals the gui key)
        rows.add(new BindRow("Open GUI", null));
        // BlockESP add-key row
        rows.add(new BindRow("BlockESP: Add block", "__blockespAdd__"));
        // One row per module
        for (Module m : ClaudeMCClient.MODULES.getModules()) {
            rows.add(new BindRow(m.getName(), m.getName()));
        }
    }
    public boolean isPauseScreen() { return false; }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);

        // Title bar
        ctx.fill(0, 0, width, 20, C_HEADER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§fKeybinds  §8— §7click a row, then press a key  |  §cDel§7=clear  §7Esc=back"),
            width / 2, 5, 0xFFFFFF);

        int startY = 24;
        int visibleRows = (height - startY - 14) / ROW_H;
        int totalRows   = rows.size();

        // Scroll clamp
        int maxScroll = Math.max(0, totalRows - visibleRows * COLS);
        scrollOffset  = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Render in two columns
        for (int i = 0; i < visibleRows * COLS; i++) {
            int rowIdx = i + scrollOffset;
            if (rowIdx >= rows.size()) break;

            int col = i % COLS;
            int colI = i / COLS;
            int rx = PAD + col * (COL_W + PAD);
            int ry = startY + colI * ROW_H;

            BindRow row = rows.get(rowIdx);
            boolean hover   = mx >= rx && mx < rx + COL_W && my >= ry && my < ry + ROW_H;
            boolean listen  = listeningRow == rowIdx;

            int bg = listen ? C_LISTEN : (hover ? C_HOV : C_ROW);
            ctx.fill(rx, ry, rx + COL_W, ry + ROW_H, bg);

            // Accent stripes for special rows
            if (row.moduleNameOrNull() == null) {
                ctx.fill(rx, ry, rx + 2, ry + ROW_H, C_WARN);
            } else if ("__blockespAdd__".equals(row.moduleNameOrNull())) {
                ctx.fill(rx, ry, rx + 2, ry + ROW_H, 0xFFFF88FF);
            }

            String currentKey;
            if (row.moduleNameOrNull() == null) {
                currentKey = KeybindManager.keyName(KeybindManager.INSTANCE.getGuiKey());
            } else if ("__blockespAdd__".equals(row.moduleNameOrNull())) {
                currentKey = KeybindManager.keyName(KeybindManager.INSTANCE.getBlockEspAddKey());
            } else {
                currentKey = KeybindManager.keyName(
                    KeybindManager.INSTANCE.getModuleBind(row.moduleNameOrNull()));
            }

            String label   = row.label();
            String keyText = listen ? "§b> press key <" : ("§7[§f" + currentKey + "§7]");

            ctx.drawText(textRenderer, Text.literal("§f" + label), rx + 6, ry + 4, C_TEXT, false);
            int keyW = textRenderer.getWidth(keyText.replaceAll("§.", ""));
            ctx.drawText(textRenderer, Text.literal(keyText), rx + COL_W - keyW - 6, ry + 4, C_TEXT, false);
        }

        // Scrollbar
        if (totalRows > visibleRows * COLS) {
            int sbX   = width - 5;
            int sbH   = height - startY - 14;
            int sbPos = startY + (int) ((float) scrollOffset / maxScroll * (sbH - 20));
            ctx.fill(sbX, startY, sbX + 4, startY + sbH, 0x33FFFFFF);
            ctx.fill(sbX, sbPos,  sbX + 4, sbPos + 20,   0xAAFFFFFF);
        }

        // Footer
        ctx.fill(0, height - 12, width, height, C_HEADER);
        ctx.drawText(textRenderer, Text.literal("§8Esc — back to ClickGUI"),
            4, height - 9, C_SUB, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int startY = 24;
        int visibleRows = (height - startY - 14) / ROW_H;

        for (int i = 0; i < visibleRows * COLS; i++) {
            int rowIdx = i + scrollOffset;
            if (rowIdx >= rows.size()) break;

            int col = i % COLS;
            int colI = i / COLS;
            int rx = PAD + col * (COL_W + PAD);
            int ry = startY + colI * ROW_H;

            if (mx >= rx && mx < rx + COL_W && my >= ry && my < ry + ROW_H) {
                listeningRow = (listeningRow == rowIdx) ? -1 : rowIdx;
                return true;
            }
        }
        listeningRow = -1;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        scrollOffset -= (int) vScroll;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningRow >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // Cancel — do not bind
                listeningRow = -1;
                return true;
            }

            BindRow row = rows.get(listeningRow);
            int newKey = (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
                ? -1 : keyCode;

            if (row.moduleNameOrNull() == null) {
                KeybindManager.INSTANCE.setGuiKey(newKey == -1 ? GLFW.GLFW_KEY_PERIOD : newKey);
            } else if ("__blockespAdd__".equals(row.moduleNameOrNull())) {
                KeybindManager.INSTANCE.setBlockEspAddKey(newKey == -1 ? GLFW.GLFW_KEY_B : newKey);
            } else {
                KeybindManager.INSTANCE.setModuleBind(row.moduleNameOrNull(), newKey);
            }
            listeningRow = -1;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            // Return to ClickGUI
            assert client != null;
            client.setScreen(new ClickGui());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        listeningRow = -1;
        super.close();
    }
}
