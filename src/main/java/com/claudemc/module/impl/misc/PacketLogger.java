package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Logs incoming chat/game packets to the in-game chat and the mod logger.
 * Useful for diagnosing vanish plugin leaks and staff communication channels.
 */
public class PacketLogger extends Module {

    public static PacketLogger INSTANCE;

    private final Deque<String> recentPackets = new ArrayDeque<>();
    private static final int MAX_LOG = 50;

    public PacketLogger() {
        super("PacketLogger", "Logs incoming message packets to chat and mod logger", Category.UTILITY);
        addBool("ChatLog",  false);
        addBool("FileLog",  true);
        addMode("Filter",   "All", "All", "Chat", "System");
        INSTANCE = this;

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, ts) -> {
            if (!isEnabled() || "System".equals(getSetting("Filter"))) return;
            logPacket("[CHAT] " + message.getString());
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isEnabled() || "Chat".equals(getSetting("Filter"))) return;
            logPacket("[GAME" + (overlay ? "/OVERLAY" : "") + "] " + message.getString());
        });
    }

    private void logPacket(String entry) {
        entry = entry.replace("\n", "\\n").replace("\r", "\\r");
        recentPackets.addLast(entry);
        while (recentPackets.size() > MAX_LOG) recentPackets.pollFirst();

        if (Boolean.parseBoolean(getSetting("FileLog"))) {
            ClaudeMCMod.LOGGER.info("[PacketLogger] {}", entry);
        }

        if (Boolean.parseBoolean(getSetting("ChatLog"))) {
            var client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§8[PLog] §7" + entry), false);
            }
        }
    }

    @Override
    public void onDisable() {
        recentPackets.clear();
    }

    public Deque<String> getRecentPackets() { return recentPackets; }

    @Override public void onTick(MinecraftClient client) {}
}
