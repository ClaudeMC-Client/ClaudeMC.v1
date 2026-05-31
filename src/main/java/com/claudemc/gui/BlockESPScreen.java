package com.claudemc.gui;

import com.claudemc.module.impl.render.BlockESP;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BlockESPScreen extends Screen {

    private static final int C_BG      = 0xE5101018;
    private static final int C_HEADER  = 0xFF18181F;
    private static final int C_ROW     = 0xFF111117;
    private static final int C_HOV     = 0xFF1C1C28;
    private static final int C_ACCENT  = 0xFF4ADE80;
    private static final int C_RED     = 0xFFFF4444;
    private static final int C_TEXT    = 0xFFEEEEEE;
    private static final int C_SUB     = 0xFF9CA3AF;
    private static final int C_INPUT   = 0xFF1C1C28;
    private static final int C_CURSOR  = 0xFFAAAAAA;

    private static final int ROW_H  = 14;
    private static final int PAD    = 6;

    // Search box
    private String searchText  = "";
    private int    cursorPos   = 0;
    private long   cursorBlink = 0;

    // Scrolling
    private int activeScroll  = 0;   // left pane (current targets)
    private int searchScroll  = 0;   // right pane (all blocks)

    // All known block IDs sorted
    private final List<String> allBlocks = new ArrayList<>();
    // Filtered view (updated when search changes)
    private List<String> filteredBlocks = new ArrayList<>();

    private String lastSearch = null;

    public BlockESPScreen() {
        super(Text.literal("BlockESP Targets"));
        // Collect all registered block IDs
        for (var entry : Registries.BLOCK.getEntrySet()) {
            allBlocks.add(entry.getKey().getValue().toString());
        }
        allBlocks.sort(Comparator.naturalOrder());
        filteredBlocks = new ArrayList<>(allBlocks);
    }

    public boolean isPauseScreen() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        assert client != null;
        int pw = Math.min(width - 40, 800);
        int ph = Math.min(height - 40, 500);
        int px = (width  - pw) / 2;
        int py = (height - ph) / 2;

        // Background
        ctx.fill(px, py, px + pw, py + ph, C_BG);
        ctx.fill(px, py, px + pw, py + 18, C_HEADER);
        ctx.drawText(textRenderer, Text.literal("§fBlockESP — Tracked Blocks"), px + PAD, py + 5, C_TEXT, false);
        ctx.drawText(textRenderer, Text.literal("§8Press §7B§8 while looking at a block to add it instantly"),
            px + PAD, py + ph - 11, C_SUB, false);

        int half = (pw - 3) / 2;

        // ── Left pane: current targets ──────────────────────────────────
        int lx = px, ly = py + 18;
        ctx.fill(lx, ly, lx + half, py + ph - 14, C_ROW);
        ctx.drawText(textRenderer, Text.literal("§7Tracked  §8(click to remove)"),
            lx + PAD, ly + 3, C_SUB, false);

        List<String> targets = new ArrayList<>(BlockESP.INSTANCE.getTargets());
        targets.sort(Comparator.naturalOrder());
        int maxVisible  = (ph - 14 - 18 - 14) / ROW_H;
        activeScroll    = clamp(activeScroll, 0, Math.max(0, targets.size() - maxVisible));

        int rowY = ly + 14;
        for (int i = activeScroll; i < targets.size() && rowY + ROW_H <= py + ph - 14; i++) {
            String id   = targets.get(i);
            boolean hov = inRect(mx, my, lx, rowY, half, ROW_H);
            ctx.fill(lx, rowY, lx + half, rowY + ROW_H, hov ? C_HOV : C_ROW);
            // Red × on right
            ctx.drawText(textRenderer, Text.literal("§c×"), lx + half - 12, rowY + 3, C_RED, false);
            // Truncate long IDs
            String label = id;
            int maxW = half - 22;
            if (textRenderer.getWidth(label) > maxW)
                label = textRenderer.trimToWidth(label, maxW) + "…";
            ctx.drawText(textRenderer, Text.literal("§f" + label), lx + PAD, rowY + 3, C_TEXT, false);
            rowY += ROW_H;
        }
        // Scroll hint
        if (targets.size() > maxVisible)
            ctx.drawText(textRenderer, Text.literal("§8↑↓ scroll"), lx + PAD, py + ph - 24, C_SUB, false);

        // ── Divider ──────────────────────────────────────────────────────
        ctx.fill(px + half, py + 18, px + half + 3, py + ph - 14, C_HEADER);

        // ── Right pane: search + all blocks ─────────────────────────────
        int rx = px + half + 3, ry = py + 18;
        int rw = pw - half - 3;
        ctx.fill(rx, ry, rx + rw, py + ph - 14, C_ROW);

        // Search box
        ctx.fill(rx + PAD, ry + 3, rx + rw - PAD, ry + 14, C_INPUT);
        String display = searchText.isEmpty() ? "§8Search blocks…" : "§f" + searchText;
        ctx.drawText(textRenderer, Text.literal(display), rx + PAD + 3, ry + 5, C_TEXT, false);
        // Blink cursor
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx2 = rx + PAD + 3 + textRenderer.getWidth(searchText);
            ctx.fill(cx2, ry + 5, cx2 + 1, ry + 13, C_CURSOR);
        }

        // Refresh filter if search changed
        if (!searchText.equals(lastSearch)) {
            lastSearch = searchText;
            filteredBlocks = allBlocks.stream()
                .filter(b -> b.contains(searchText.toLowerCase()))
                .toList();
            searchScroll = 0;
        }

        int sRowY = ry + 18;
        int sMax  = (ph - 14 - 18 - 18) / ROW_H;
        searchScroll = clamp(searchScroll, 0, Math.max(0, filteredBlocks.size() - sMax));

        for (int i = searchScroll; i < filteredBlocks.size() && sRowY + ROW_H <= py + ph - 14; i++) {
            String id   = filteredBlocks.get(i);
            boolean hov = inRect(mx, my, rx, sRowY, rw, ROW_H);
            boolean tracked = BlockESP.INSTANCE.getTargets().contains(id);
            ctx.fill(rx, sRowY, rx + rw, sRowY + ROW_H, hov ? C_HOV : C_ROW);
            String label = id;
            int maxW = rw - 22;
            if (textRenderer.getWidth(label) > maxW)
                label = textRenderer.trimToWidth(label, maxW) + "…";
            String prefix = tracked ? "§a✔ " : "§8  ";
            ctx.drawText(textRenderer, Text.literal(prefix + "§f" + label), rx + PAD, sRowY + 3, C_TEXT, false);
            sRowY += ROW_H;
        }
        if (filteredBlocks.size() > sMax)
            ctx.drawText(textRenderer, Text.literal("§8↑↓ scroll"), rx + PAD, py + ph - 24, C_SUB, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        assert client != null;
        int pw = Math.min(width - 40, 800);
        int ph = Math.min(height - 40, 500);
        int px = (width  - pw) / 2;
        int py = (height - ph) / 2;
        int half = (pw - 3) / 2;

        if (button != 0) return super.mouseClicked(mx, my, button);

        // Left pane: remove target on click
        List<String> targets = new ArrayList<>(BlockESP.INSTANCE.getTargets());
        targets.sort(Comparator.naturalOrder());
        int maxVisible = (ph - 14 - 18 - 14) / ROW_H;
        int rowY = py + 18 + 14;
        for (int i = activeScroll; i < targets.size() && rowY + ROW_H <= py + ph - 14; i++) {
            if (inRect((int)mx, (int)my, px, rowY, half, ROW_H)) {
                BlockESP.INSTANCE.removeTarget(targets.get(i));
                BlockESP.INSTANCE.saveCustomBlocks();
                return true;
            }
            rowY += ROW_H;
        }

        // Right pane: add/remove on click
        int rx = px + half + 3, ry = py + 18;
        int rw = pw - half - 3;
        int sMax  = (ph - 14 - 18 - 18) / ROW_H;
        int sRowY = ry + 18;
        for (int i = searchScroll; i < filteredBlocks.size() && sRowY + ROW_H <= py + ph - 14; i++) {
            if (inRect((int)mx, (int)my, rx, sRowY, rw, ROW_H)) {
                String id = filteredBlocks.get(i);
                if (BlockESP.INSTANCE.getTargets().contains(id))
                    BlockESP.INSTANCE.removeTarget(id);
                else
                    BlockESP.INSTANCE.addTarget(id);
                BlockESP.INSTANCE.saveCustomBlocks();
                return true;
            }
            sRowY += ROW_H;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        int pw = Math.min(width - 40, 800);
        int px = (width  - pw) / 2;
        int half = (pw - 3) / 2;
        if (mx < px + half)
            activeScroll = Math.max(0, activeScroll - (int) vScroll);
        else
            searchScroll = Math.max(0, searchScroll - (int) vScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { assert client != null; client.setScreen(new ClickGui()); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE && cursorPos > 0) {
            searchText = searchText.substring(0, cursorPos - 1) + searchText.substring(cursorPos);
            cursorPos--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE && cursorPos < searchText.length()) {
            searchText = searchText.substring(0, cursorPos) + searchText.substring(cursorPos + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT  && cursorPos > 0)                    { cursorPos--; return true; }
        if (key == GLFW.GLFW_KEY_RIGHT && cursorPos < searchText.length())   { cursorPos++; return true; }
        if (key == GLFW.GLFW_KEY_HOME)  { cursorPos = 0;                  return true; }
        if (key == GLFW.GLFW_KEY_END)   { cursorPos = searchText.length(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        searchText = searchText.substring(0, cursorPos) + c + searchText.substring(cursorPos);
        cursorPos++;
        return true;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
