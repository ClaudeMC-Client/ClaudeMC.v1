package com.claudemc.module.impl.misc;

import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two features in one module:
 *
 * 1) CHAT ASSISTANT — intercepts outgoing messages that start with the trigger
 *    prefix (default "!ai ") and sends the rest to the configured AI, printing
 *    the reply back to local chat without sending anything to the server.
 *
 * 2) PACKET NARRATION — periodically sends the last N PacketLogger entries to
 *    the AI and asks it to summarise what the server is doing, in plain English.
 *
 * Requires at least one API key in [AI] settings.
 */
public class AIAssist extends Module {

    public static AIAssist INSTANCE;

    private final AtomicBoolean busy = new AtomicBoolean(false);

    // Packet narration
    private int narrateTimer = 0;

    public AIAssist() {
        super("AIAssist",
              "In-game AI chat assistant (!ai <question>) + periodic packet narration",
              Category.MISC);
        addSetting("Prefix",        "!ai ");
        addBool("PacketNarration",  false);
        addNumber("NarrateTicks",   600, 100, 6000, 100, true);
        INSTANCE = this;

        // Intercept outgoing chat messages — cancel ones matching the prefix and send to AI instead
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!isEnabled()) return true;
            String prefix = getSetting("Prefix").toLowerCase();
            if (!message.toLowerCase().startsWith(prefix)) return true;

            String question = message.substring(prefix.length()).trim();
            if (question.isBlank()) return false;

            handleQuestion(question);
            return false; // block the message from reaching the server
        });
    }

    private void handleQuestion(String question) {
        if (!AIConfig.INSTANCE.isConfigured()) {
            var client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("§c[AIAssist] No API key — open [AI] in the ClickGUI."), false);
            }
            return;
        }

        if (busy.get()) {
            var client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("§c[AIAssist] Already waiting for a response…"), false);
            }
            return;
        }

        busy.set(true);
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("§8[AIAssist] §7Asking " + AIConfig.INSTANCE.provider + "…"), false);
        }

        AIClient.INSTANCE.ask(question,
            response -> {
                busy.set(false);
                var mc = MinecraftClient.getInstance();
                if (mc.player == null) return;
                // Split long responses at sentence boundaries
                for (String line : splitResponse(response)) {
                    mc.player.sendMessage(
                        Text.literal("§b[AI] §f" + line), false);
                }
            },
            err -> {
                busy.set(false);
                var mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.player.sendMessage(
                        Text.literal("§c[AIAssist] Error: " + err), false);
                }
            }
        );
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (!Boolean.parseBoolean(getSetting("PacketNarration"))) return;
        if (PacketLogger.INSTANCE == null || !PacketLogger.INSTANCE.isEnabled()) return;
        if (busy.get()) return;

        int interval = parseInt(getSetting("NarrateTicks"), 600);
        if (++narrateTimer < interval) return;
        narrateTimer = 0;

        var packets = PacketLogger.INSTANCE.getRecentPackets();
        if (packets.isEmpty()) return;

        // Take last 20 packets
        var recent = packets.stream().skip(Math.max(0, packets.size() - 20)).toList();
        String packetDump = String.join("\n", recent);

        String prompt = "Here are the last " + recent.size() + " Minecraft network packets "
            + "logged between my client and the server:\n\n"
            + packetDump
            + "\n\nIn 2-3 sentences, explain what the server appears to be doing "
            + "and whether anything looks unusual (e.g. hidden packets, suspicious timing, "
            + "unexpected channels).";

        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("§8[AIAssist/Narrate] §7Analysing " + recent.size() + " packets…"), false);
        }

        busy.set(true);
        AIClient.INSTANCE.ask(prompt,
            response -> {
                busy.set(false);
                var mc = MinecraftClient.getInstance();
                if (mc.player == null) return;
                for (String line : splitResponse(response)) {
                    mc.player.sendMessage(Text.literal("§b[PacketAI] §f" + line), false);
                }
            },
            err -> {
                busy.set(false);
                var mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("§c[PacketAI] Error: " + err), false);
                }
            }
        );
    }

    @Override
    public void onDisable() {
        narrateTimer = 0;
        busy.set(false);
    }

    /** Splits response at natural boundaries so each chat line is readable. */
    private static String[] splitResponse(String text) {
        // Chat lines max ~256 chars; split on ". " or "\n"
        return text.replace("\n", " ").split("(?<=\\.\\s)|(?<=\\!\\s)|(?<=\\?\\s)");
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
