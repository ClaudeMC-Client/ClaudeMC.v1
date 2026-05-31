package com.claudemc.gui;

import com.claudemc.account.AltManager;
import com.claudemc.account.AltManager.AltEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Alt account manager screen.
 * Open from ClickGUI → [Alts].
 *
 * Click row = switch to that alt.
 * Right-click = remove.
 * [+ Offline] = add offline/cracked account.
 * [+ Session] = add account with access token.
 * [Restore]   = switch back to original account.
 */
public class AltScreen extends Screen {

    private static final int C_BG     = 0xE5101018;
    private static final int C_HEADER = 0xFF18181F;
    private static final int C_ROW    = 0xFF111117;
    private static final int C_HOV    = 0xFF1C1C28;
    private static final int C_ACCENT = 0xFF44AAFF;
    private static final int C_TEXT   = 0xFFEEEEEE;
    private static final int C_SUB    = 0xFF9CA3AF;
    private static final int C_GREEN  = 0xFF4ADE80;
    private static final int C_WARN   = 0xFFFFAA44;

    private static final int ROW_H = 18;
    private static final int PAD   = 6;

    // Add-account form state
    private enum AddMode { NONE, OFFLINE, SESSION }
    private AddMode addMode = AddMode.NONE;
    private int     addField = 0;   // 0=name, 1=uuid, 2=token (SESSION only)
    private String  fName  = "";
    private String  fUuid  = "";
    private String  fToken = "";

    private String statusMsg = "";
    private int    scrollOffset = 0;

    public AltScreen() {
        super(Text.literal("ClaudeMC — Alt Manager"));
    }

    public boolean isPauseScreen() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        ctx.fill(0, 0, width, 20, C_HEADER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§fAlt Manager  §8|  §7LClick=switch  RClick=remove"), width / 2, 5, 0xFFFFFF);

        // Current session info bar
        String active = "§7Active: §f" + AltManager.INSTANCE.getActiveUsername()
            + (AltManager.INSTANCE.isUsingAlt() ? " §6[ALT]" : " §a[MAIN]");
        ctx.fill(0, 20, width, 32, 0xFF0D0D14);
        ctx.drawText(textRenderer, Text.literal(active), PAD, 23, C_TEXT, false);

        List<AltEntry> alts = AltManager.INSTANCE.getAlts();
        int startY  = 34;
        int footerH = addMode == AddMode.NONE ? 28 : 56;
        int visRows = (height - startY - footerH) / ROW_H;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, alts.size() - visRows)));

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= alts.size()) break;
            AltEntry a = alts.get(idx);
            int ry    = startY + i * ROW_H;
            boolean hover = mx >= PAD && mx < width - PAD && my >= ry && my < ry + ROW_H;
            ctx.fill(PAD, ry, width - PAD, ry + ROW_H, hover ? C_HOV : C_ROW);
            ctx.fill(PAD, ry, PAD + 3, ry + ROW_H,
                a.type == AltManager.AltType.OFFLINE ? C_WARN : C_ACCENT);
            ctx.drawText(textRenderer,
                Text.literal("§f" + a.name + "  §8[" + a.type.name() + "]"),
                PAD + 6, ry + 5, C_TEXT, false);
        }

        drawFooter(ctx, mx, my, footerH);
    }

    private void drawFooter(DrawContext ctx, int mx, int my, int footerH) {
        int fy = height - footerH;
        ctx.fill(0, fy, width, height, C_HEADER);

        drawBtn(ctx, mx, my, PAD,       fy + 6, 90, 14, "§f[+ Offline]", C_WARN);
        drawBtn(ctx, mx, my, PAD + 98,  fy + 6, 90, 14, "§f[+ Session]", C_ACCENT);
        if (AltManager.INSTANCE.isUsingAlt())
            drawBtn(ctx, mx, my, PAD + 196, fy + 6, 70, 14, "§f[Restore]", C_GREEN);

        if (!statusMsg.isEmpty())
            ctx.drawText(textRenderer, Text.literal("§7" + statusMsg), PAD + 274, fy + 9, C_SUB, false);

        if (addMode != AddMode.NONE) {
            int iy = fy + 24;
            String title = addMode == AddMode.OFFLINE ? "§eNew Offline Account" : "§bNew Session Account";
            ctx.drawText(textRenderer, Text.literal(title), PAD, iy - 2, C_TEXT, false);
            iy += 10;
            ctx.drawText(textRenderer, Text.literal(
                field(0, "Username", fName) +
                (addMode == AddMode.SESSION
                    ? "  " + field(1, "UUID", fUuid) + "  " + field(2, "AccessToken", fToken)
                    : "")
                + "  §8[Enter=add  Esc=cancel  Tab=next]"),
                PAD, iy, C_TEXT, false);
        }
    }

    private String field(int idx, String label, String val) {
        boolean sel = addField == idx;
        return (sel ? "§b" : "§8") + label + ": §f" + val + (sel ? "§b|" : "");
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
        int footerH = addMode == AddMode.NONE ? 28 : 56;
        int fy = height - footerH;

        if (y >= fy + 6 && y < fy + 20) {
            if (x >= PAD && x < PAD + 90) { startAdd(AddMode.OFFLINE); return true; }
            if (x >= PAD + 98 && x < PAD + 188) { startAdd(AddMode.SESSION); return true; }
            if (AltManager.INSTANCE.isUsingAlt() && x >= PAD + 196 && x < PAD + 266) {
                boolean ok = AltManager.INSTANCE.restore();
                statusMsg = ok ? "Restored original account." : "Restore failed.";
                return true;
            }
        }

        List<AltEntry> alts = AltManager.INSTANCE.getAlts();
        int startY  = 34;
        int visRows = (height - startY - footerH) / ROW_H;
        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= alts.size()) break;
            int ry = startY + i * ROW_H;
            if (x >= PAD && x < width - PAD && y >= ry && y < ry + ROW_H) {
                if (button == 1) { AltManager.INSTANCE.remove(idx); statusMsg = "Removed."; return true; }
                if (button == 0) {
                    boolean ok = AltManager.INSTANCE.switchTo(idx);
                    statusMsg = ok ? "Switched to " + alts.get(idx).name + "." : "Switch failed.";
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
        if (addMode != AddMode.NONE) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> { addMode = AddMode.NONE; return true; }
                case GLFW.GLFW_KEY_ENTER  -> { commitAdd(); return true; }
                case GLFW.GLFW_KEY_TAB    -> {
                    int max = addMode == AddMode.OFFLINE ? 1 : 3;
                    addField = (addField + 1) % max;
                    return true;
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (addField == 0 && !fName.isEmpty())  fName  = fName.substring(0, fName.length() - 1);
                    if (addField == 1 && !fUuid.isEmpty())  fUuid  = fUuid.substring(0, fUuid.length() - 1);
                    if (addField == 2 && !fToken.isEmpty()) fToken = fToken.substring(0, fToken.length() - 1);
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
        if (addMode != AddMode.NONE && c >= 32) {
            if (addField == 0) fName  += c;
            if (addField == 1) fUuid  += c;
            if (addField == 2) fToken += c;
            return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void startAdd(AddMode mode) {
        addMode = mode; addField = 0;
        fName = ""; fUuid = ""; fToken = "";
    }

    private void commitAdd() {
        if (fName.isBlank()) { statusMsg = "Name cannot be blank."; return; }
        if (addMode == AddMode.OFFLINE) {
            AltManager.INSTANCE.addOffline(fName);
            statusMsg = "Added offline alt: " + fName;
        } else {
            if (fUuid.isBlank() || fToken.isBlank()) { statusMsg = "UUID and token required."; return; }
            AltManager.INSTANCE.addSession(fName, fUuid, fToken);
            statusMsg = "Added session alt: " + fName;
        }
        addMode = AddMode.NONE;
    }
}
