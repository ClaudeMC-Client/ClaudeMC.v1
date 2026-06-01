package com.claudemc.module.impl.misc;

import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI-powered chat responder. When a message looks like a staff AFK check or DM,
 * it generates a contextually believable human reply via the configured AI provider.
 *
 * Falls back to AutoReply's rule-based system when AI is not configured or the
 * provider returns an error.
 *
 * Requires at least one API key set in [AI] settings.
 */
public class SmartReply extends Module {

    public static SmartReply INSTANCE;

    private final AtomicBoolean waiting = new AtomicBoolean(false);
    private String pendingReply  = null;
    private int    replyDelay    = 0;

    // The system prompt injected before every reply request
    private static final String REPLY_SYSTEM =
        "You are a Minecraft player (online multiplayer). A staff member or player just sent you a message that might be an AFK check or question. " +
        "Reply as a normal human player would — casual, short (5-15 words max), maybe slightly annoyed at being interrupted. " +
        "Do NOT say you are an AI. Do NOT use markdown. Return ONLY the reply text.";

    public SmartReply() {
        super("SmartReply",
              "AI-generated replies to AFK checks and DMs — sounds genuinely human",
              Category.MISC);
        addNumber("DelayTicks", 25, 5, 120, 5, true);
        addBool("AnyDM",        false);  // reply to any whisper, not just AFK phrases
        INSTANCE = this;

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, ts) -> {
            if (!isEnabled()) return;
            handleIncoming(message.getString());
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isEnabled() || overlay) return;
            handleIncoming(message.getString());
        });
    }

    private void handleIncoming(String raw) {
        if (waiting.get() || pendingReply != null) return;

        String lower = raw.toLowerCase();
        boolean isDM         = looksLikeDM(lower);
        boolean isAFKCheck   = isAFKCheck(lower);
        boolean replyAll     = Boolean.parseBoolean(getSetting("AnyDM"));

        if (!isAFKCheck && !(replyAll && isDM)) return;

        if (!AIConfig.INSTANCE.isConfigured()) {
            // Fall back to rule-based if no API key
            scheduleReply("yeah im here, what's up");
            return;
        }

        waiting.set(true);
        String prompt = "A player in Minecraft sent me this message: \"" + raw + "\"\n"
            + "Reply naturally as a human Minecraft player would.";

        // Override system prompt for this request type
        String originalSys = AIConfig.INSTANCE.systemPrompt;
        AIConfig.INSTANCE.systemPrompt = REPLY_SYSTEM;

        AIClient.INSTANCE.ask(prompt,
            reply -> {
                AIConfig.INSTANCE.systemPrompt = originalSys;
                scheduleReply(reply.trim());
                waiting.set(false);
            },
            err -> {
                AIConfig.INSTANCE.systemPrompt = originalSys;
                scheduleReply("yeah im here");
                waiting.set(false);
            }
        );
    }

    private void scheduleReply(String reply) {
        pendingReply = reply;
        replyDelay   = parseInt(getSetting("DelayTicks"), 25);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (pendingReply == null) return;
        if (--replyDelay > 0) return;
        if (client.player == null || client.getNetworkHandler() == null) { pendingReply = null; return; }

        client.getNetworkHandler().sendChatMessage(pendingReply);
        client.player.sendMessage(Text.literal("§8[SmartReply] §7sent: §f" + pendingReply), false);
        pendingReply = null;
    }

    @Override
    public void onDisable() {
        pendingReply = null;
        waiting.set(false);
    }

    private boolean isAFKCheck(String lower) {
        return lower.contains("afk") || lower.contains("you there") || lower.contains("u there")
            || lower.contains("hello?") || lower.contains("macro") || lower.contains("bot")
            || lower.contains("autoclicker") || lower.contains("are you") || lower.contains("still here");
    }

    private boolean looksLikeDM(String lower) {
        return lower.contains("->") || lower.contains("msg")
            || lower.contains("whisper") || lower.contains("tell");
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
