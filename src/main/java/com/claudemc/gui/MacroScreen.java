package com.claudemc.gui;

import com.claudemc.chat.MacroManager;
import com.claudemc.chat.MacroManager.Macro;
import com.claudemc.keybind.KeybindManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Macro editor screen.
 * Accessible from ClickGUI footer → [Macros].
 *
 * Lists all macros.  Bottom: [+ New] button.
 * Click row → edit mode inline: name, command, keybind.
 * Right-click row → delete.
 */
public class MacroScreen extends Screen {

    private static final int C_BG     = 0xE5101018;
    private static final int C_HEADER = 0xFF18181F;
    private static final int C_ROW    = 0xFF111117;
    private static final int C_HOV    = 0xFF1C1C28;
    private static final int C_SEL    = 0xFF1A1A2E;
    private static final int C_ACCENT = 0xFFFFAA44;
    private static final int C_TEXT   = 0xFFEEEEEE;
    private static final int C_SUB    = 0xFF9CA3AF;
    private static final int C_GREEN  = 0xFF4ADE80;

    private static final int ROW_H = 20;
    private static final int PAD   = 6;

    private int selectedIdx = -1;  // row being edited

    // Edit fields
    private String editName    = "";
    private String editCommand = "";
    private int    editKeybind = -1;

    // Which field is focused in edit mode (0=name, 1=command, 2=keybind)
    private int focusField  = 0;
    private boolean listeningKey = false;

    private int scrollOffset = 0;

    public MacroScreen() {
        super(Text.literal("ClaudeMC — Macros"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        ctx.fill(0, 0, width, 20, C_HEADER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§fMacros  §8|  §7LClick=edit  RClick=delete  §8|  §7Esc=back"),
            width / 2, 5, 0xFFFFFF);

        List<Macro> macros = MacroManager.INSTANCE.getMacros();
        int startY  = 24;
        int footerH = 28;
        int visRows = (height - startY - footerH) / ROW_H;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, macros.size() - visRows)));

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= macros.size()) break;
            Macro m   = macros.get(idx);
            int ry    = startY + i * ROW_H;
            boolean hover = mx >= PAD && mx < width - PAD && my >= ry && my < ry + ROW_H;
            boolean sel   = selectedIdx == idx;

            ctx.fill(PAD, ry, width - PAD, ry + ROW_H, sel ? C_SEL : (hover ? C_HOV : C_ROW));
            ctx.fill(PAD, ry, PAD + 3, ry + ROW_H, C_ACCENT);

            if (sel) {
                drawEditRow(ctx, ry, m);
            } else {
                String keyHint = (m.keybind != -1) ? " §8[" + KeybindManager.keyName(m.keybind) + "]" : "";
                ctx.drawText(textRenderer,
                    Text.literal("§f" + m.name + "  §8→  §7" + m.command + keyHint),
                    PAD + 6, ry + 6, C_TEXT, false);
            }
        }

        // Footer
        ctx.fill(0, height - footerH, width, height, C_HEADER);
        drawBtn(ctx, mx, my, PAD, height - footerH + 6, 80, 14, "§f[+ New Macro]", C_GREEN);
        ctx.drawText(textRenderer,
            Text.literal("§8" + macros.size() + " macro" + (macros.size() == 1 ? "" : "s")),
            PAD + 88, height - footerH + 9, C_SUB, false);
    }

    private void drawEditRow(DrawContext ctx, int ry, Macro m) {
        int x = PAD + 6;
        // Name field
        String nameDisp = (focusField == 0 ? "§b" : "§7") + "Name: §f" + editName + (focusField == 0 ? "§b|" : "");
        ctx.drawText(textRenderer, Text.literal(nameDisp), x, ry + 2, C_TEXT, false);

        int nameW = textRenderer.getWidth("Name: " + editName + "  ");
        // Command field
        String cmdDisp = (focusField == 1 ? "§b" : "§7") + "Cmd: §f" + editCommand + (focusField == 1 ? "§b|" : "");
        ctx.drawText(textRenderer, Text.literal(cmdDisp), x + nameW + 6, ry + 2, C_TEXT, false);

        // Keybind field
        String keyDisp = listeningKey ? "§bPress key…"
            : (focusField == 2 ? "§b" : "§7") + "Key: §f" + KeybindManager.keyName(editKeybind);
        ctx.drawText(textRenderer, Text.literal(keyDisp), x, ry + 11, C_TEXT, false);

        // Save hint
        ctx.drawText(textRenderer, Text.literal("§8[Enter=save  Tab=next field  Del=clear key]"),
            x + textRenderer.getWidth(keyDisp.replaceAll("§.", "")) + 8, ry + 11, C_SUB, false);
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
    public boolean mouseClicked(double mx, double my, int button) {
        int x = (int) mx, y = (int) my;

        // Footer buttons
        if (y >= height - 22 && y < height - 8 && x >= PAD && x < PAD + 80 && button == 0) {
            addNew();
            return true;
        }

        List<Macro> macros = MacroManager.INSTANCE.getMacros();
        int startY  = 24, footerH = 28;
        int visRows = (height - startY - footerH) / ROW_H;

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= macros.size()) break;
            int ry = startY + i * ROW_H;
            if (x >= PAD && x < width - PAD && y >= ry && y < ry + ROW_H) {
                if (button == 1) {  // right-click = delete
                    if (selectedIdx == idx) selectedIdx = -1;
                    MacroManager.INSTANCE.remove(idx);
                    return true;
                }
                if (button == 0) {
                    if (selectedIdx == idx) {
                        saveEdit(idx);
                    } else {
                        beginEdit(idx, macros.get(idx));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scrollOffset -= (int) vy;
        return true;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedIdx >= 0) {
            if (listeningKey) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { listeningKey = false; return true; }
                editKeybind = (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) ? -1 : keyCode;
                listeningKey = false;
                return true;
            }
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> { cancelEdit(); return true; }
                case GLFW.GLFW_KEY_ENTER  -> { saveEdit(selectedIdx); return true; }
                case GLFW.GLFW_KEY_TAB    -> { focusField = (focusField + 1) % 3; if (focusField == 2) listeningKey = true; return true; }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (focusField == 0 && !editName.isEmpty())    editName    = editName.substring(0, editName.length() - 1);
                    if (focusField == 1 && !editCommand.isEmpty()) editCommand = editCommand.substring(0, editCommand.length() - 1);
                    return true;
                }
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            assert client != null;
            client.setScreen(new ClickGui());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (selectedIdx >= 0 && !listeningKey && c >= 32) {
            if (focusField == 0 && editName.length()    < 32)  editName    += c;
            if (focusField == 1 && editCommand.length() < 256) editCommand += c;
            return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void addNew() {
        MacroManager.INSTANCE.add("NewMacro", "/say hello", -1);
        int idx = MacroManager.INSTANCE.getMacros().size() - 1;
        beginEdit(idx, MacroManager.INSTANCE.getMacros().get(idx));
    }

    private void beginEdit(int idx, Macro m) {
        selectedIdx  = idx;
        editName     = m.name;
        editCommand  = m.command;
        editKeybind  = m.keybind;
        focusField   = 0;
        listeningKey = false;
    }

    private void saveEdit(int idx) {
        MacroManager.INSTANCE.update(idx, editName, editCommand, editKeybind);
        selectedIdx = -1;
    }

    private void cancelEdit() { selectedIdx = -1; }
}
