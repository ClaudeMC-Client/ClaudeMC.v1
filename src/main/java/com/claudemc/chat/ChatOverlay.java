package com.claudemc.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Floating chat-input overlay that renders on top of any open screen
 * (auction house, crafting table, chest, etc.) without closing it.
 *
 * Toggle with the bound key (default: T — same as vanilla, but works inside GUIs).
 * Enter = send.  Esc = dismiss.  Supports full editing (backspace, left/right, home/end).
 */
public class ChatOverlay {

    public static final ChatOverlay INSTANCE = new ChatOverlay();

    private boolean active = false;
    private final StringBuilder buffer = new StringBuilder();
    private int cursor = 0;

    // Called every frame from HudManager to render the overlay
    public void render(net.minecraft.client.gui.DrawContext ctx, MinecraftClient client) {
        if (!active) return;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        int boxY  = sh - 22;
        int boxH  = 14;
        int boxX  = 2;
        int boxW  = sw - 4;

        // Background
        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC000000);
        ctx.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF4ADE80);

        // Text with cursor
        String before = buffer.substring(0, cursor);
        String after  = buffer.substring(cursor);
        String display = "§f" + before + "§a|§f" + after;
        ctx.drawText(client.textRenderer, net.minecraft.text.Text.literal(display),
            boxX + 3, boxY + 3, 0xFFFFFF, true);

        // Prompt label
        ctx.drawText(client.textRenderer,
            net.minecraft.text.Text.literal("§8[Chat]"),
            boxX + 3, boxY - 10, 0x888888, false);
    }

    /** Returns true if the overlay consumed the key event. */
    public boolean keyPressed(int keyCode, MinecraftClient client) {
        if (!active) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> { dismiss(); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { send(client); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) { buffer.deleteCharAt(cursor - 1); cursor--; }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < buffer.length()) buffer.deleteCharAt(cursor);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT  -> { if (cursor > 0) cursor--;                return true; }
            case GLFW.GLFW_KEY_RIGHT -> { if (cursor < buffer.length()) cursor++;  return true; }
            case GLFW.GLFW_KEY_HOME  -> { cursor = 0;                              return true; }
            case GLFW.GLFW_KEY_END   -> { cursor = buffer.length();                return true; }
        }
        return true; // consume all keys while active
    }

    /** Returns true if the overlay consumed the char. */
    public boolean charTyped(char c) {
        if (!active) return false;
        if (c >= 32 && buffer.length() < 256) {
            buffer.insert(cursor, c);
            cursor++;
        }
        return true;
    }

    public void toggle() {
        if (active) dismiss();
        else        open();
    }

    public void open() {
        active = true;
        buffer.setLength(0);
        cursor = 0;
    }

    public void dismiss() {
        active = false;
        buffer.setLength(0);
        cursor = 0;
    }

    public boolean isActive() { return active; }

    private void send(MinecraftClient client) {
        String text = buffer.toString().trim();
        dismiss();
        if (text.isEmpty() || client.player == null) return;
        if (text.startsWith("/")) {
            client.getNetworkHandler().sendChatCommand(text.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(text);
        }
    }
}
