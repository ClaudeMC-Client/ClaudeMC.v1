package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * InfiniChat: Sends repeated chat messages with anti-spam bypass techniques.
 * Appends random invisible characters or counters to bypass duplicate-message filters.
 */
public class InfiniChat extends Module {

    public static InfiniChat INSTANCE;

    private int tickCounter = 0;
    private int msgCounter = 0;
    private final Random random = new Random();

    // Invisible Unicode characters for bypass
    private static final char[] BYPASS_CHARS = {
        '​', '‌', '‍', '⁠', '﻿'
    };

    public InfiniChat() {
        super("InfiniChat", "Sends repeated chat messages with anti-spam bypass", Category.CHAT);
        INSTANCE = this;
        addSetting("Message", "Hello!");
        addNumber("DelayTicks", 40, 1, 600, 1, true);
        addMode("BypassMode", "Invisible", "Invisible", "Counter", "None");
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        msgCounter = 0;
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
        msgCounter = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int delay = parseInt(getSetting("DelayTicks"), 40);
        if (++tickCounter < delay) return;
        tickCounter = 0;

        String message = getSetting("Message");
        if (message.isBlank()) return;

        String bypassMode = getSetting("BypassMode");
        String finalMsg;

        switch (bypassMode) {
            case "Invisible" -> {
                char bypassChar = BYPASS_CHARS[msgCounter % BYPASS_CHARS.length];
                finalMsg = message + bypassChar;
            }
            case "Counter" -> finalMsg = message + " " + (++msgCounter);
            default -> finalMsg = message;
        }
        msgCounter++;

        mc.getNetworkHandler().sendChatMessage(finalMsg);
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
