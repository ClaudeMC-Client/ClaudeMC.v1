package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Filters duplicate and repetitive chat messages so they don't clutter the chat.
 */
public class AntiSpam extends Module {

    private final Deque<String> recentMessages = new ArrayDeque<>();

    public AntiSpam() {
        super("AntiSpam", "Hides duplicate and repetitive chat messages", Category.MISC);
        addNumber("HistorySize", 5, 1, 20, 1, true);
        addBool("FilterDupes", true);
        addBool("FilterAds",   false);

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, ts) -> {
            if (!isEnabled()) return true;
            String text = message.getString();
            return shouldAllow(text);
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!isEnabled() || overlay) return true;
            String text = message.getString();
            return shouldAllow(text);
        });
    }

    private boolean shouldAllow(String text) {
        int histSize = parseInt(getSetting("HistorySize"), 5);

        if (Boolean.parseBoolean(getSetting("FilterDupes"))) {
            if (recentMessages.contains(text)) return false;
        }

        if (Boolean.parseBoolean(getSetting("FilterAds"))) {
            String lower = text.toLowerCase();
            if (lower.contains("shop.") || lower.contains("store.") || lower.contains("buy now")
             || (lower.contains("discord.gg") && !lower.contains("server"))) return false;
        }

        recentMessages.addLast(text);
        while (recentMessages.size() > histSize) recentMessages.pollFirst();
        return true;
    }

    @Override
    public void onDisable() {
        recentMessages.clear();
    }

    @Override public void onTick(MinecraftClient client) {}

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
