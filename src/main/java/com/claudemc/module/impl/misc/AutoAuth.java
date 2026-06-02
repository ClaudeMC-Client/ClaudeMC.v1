package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * AutoAuth — automatically registers/logs in on cracked servers that require auth.
 *
 * Listens for login prompts (common phrases from AuthMe, NLogin, FastLogin, etc.)
 * and sends /register or /login with the configured password after a short delay.
 *
 * Works with: AuthMe Reloaded, NLogin, FastLogin, JPremium, and most other auth plugins.
 */
public class AutoAuth extends Module {

    public static AutoAuth INSTANCE;

    private boolean loggedIn  = false;
    private int     delayLeft = 0;
    private String  pendingCmd = null;

    public AutoAuth() {
        super("AutoAuth", "Auto-registers/logs in on cracked servers (AuthMe, NLogin, etc.)", Category.EXPLOIT);
        addSetting("Password", "changeme123");
        addNumber("DelayTicks", 20, 5, 100, 5, true);
        INSTANCE = this;

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!isEnabled() || loggedIn) return;
            String lower = msg.getString().toLowerCase();
            if (isLoginPrompt(lower)) scheduleCmd("/login " + getSetting("Password"));
            else if (isRegisterPrompt(lower)) scheduleCmd("/register " + getSetting("Password") + " " + getSetting("Password"));
            else if (lower.contains("logged in") || lower.contains("authenticated") || lower.contains("welcome back")) {
                loggedIn = true;
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§a[AutoAuth] §7Authenticated."), false);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { loggedIn = false; pendingCmd = null; });
    }

    private void scheduleCmd(String cmd) {
        if (pendingCmd != null) return; // already queued
        pendingCmd = cmd;
        delayLeft  = parseInt(getSetting("DelayTicks"), 20);
    }

    @Override public void onEnable()  { loggedIn = false; pendingCmd = null; }
    @Override public void onDisable() { pendingCmd = null; }

    @Override
    public void onTick(MinecraftClient client) {
        if (pendingCmd == null || client.player == null || client.getNetworkHandler() == null) return;
        if (--delayLeft > 0) return;
        client.getNetworkHandler().sendChatCommand(pendingCmd.substring(1));
        pendingCmd = null;
    }

    private boolean isLoginPrompt(String s) {
        return s.contains("/login") || s.contains("please login") || s.contains("log in to play")
            || s.contains("you need to login") || s.contains("type /login");
    }

    private boolean isRegisterPrompt(String s) {
        return s.contains("/register") || s.contains("please register") || s.contains("type /register")
            || s.contains("not registered") || s.contains("register to play");
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
