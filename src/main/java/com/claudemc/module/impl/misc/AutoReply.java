package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Automatically replies to incoming chat messages that match configured trigger patterns.
 *
 * Default rules cover common staff AFK-check phrases like:
 *   "are you afk", "afk check", "hello?", "you there", etc.
 *
 * Reply is sent as a chat message after a short human-like delay.
 */
public class AutoReply extends Module {

    public static AutoReply INSTANCE;

    /** trigger substring (lowercase) → reply to send */
    private final Map<String, String> rules = new LinkedHashMap<>();

    private int replyDelay  = 0;
    private String pendingReply = null;

    public AutoReply() {
        super("AutoReply", "Auto-replies to chat messages matching staff AFK-check patterns", Category.CHAT);
        addNumber("DelayTicks", 20, 5, 100, 5, true);
        addBool("ReplyAll", false);   // reply to every DM, not just matched patterns
        INSTANCE = this;

        // Default AFK-check triggers → plausible human replies
        rules.put("afk check",     "No, I'm here! Just a bit slow lol");
        rules.put("are you afk",   "No I'm here, sorry");
        rules.put("you there",     "Yeah I'm here");
        rules.put("hello?",        "Hey! Sorry, was tabbed out");
        rules.put("u there",       "Yeah I'm here");
        rules.put("still here",    "Yep, still here!");
        rules.put("doing anything","Just farming, why?");
        rules.put("what are you doing", "Just grinding, nothing much");
        rules.put("macro",         "What? No I'm not, just playing normally");
        rules.put("autoclicker",   "No I'm not, lol");
        rules.put("hacked",        "I don't hack lmao");

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!isEnabled()) return;
            handleMessage(message.getString());
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isEnabled() || overlay) return;
            handleMessage(message.getString());
        });
    }

    private void handleMessage(String raw) {
        if (pendingReply != null) return; // already have a reply queued

        String lower = raw.toLowerCase();
        boolean replyAll = Boolean.parseBoolean(getSetting("ReplyAll"));

        // Check if this looks like a DM directed at us
        boolean isDm = lower.contains("->") || lower.contains("whisper")
                    || lower.contains("msg") || lower.contains("tell");

        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (lower.contains(rule.getKey())) {
                scheduleReply(rule.getValue());
                return;
            }
        }

        if (replyAll && isDm) {
            scheduleReply("Hey, what's up?");
        }
    }

    private void scheduleReply(String reply) {
        pendingReply = reply;
        replyDelay   = parseInt(getSetting("DelayTicks"), 20);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (pendingReply == null) return;
        if (--replyDelay > 0) return;
        if (client.player == null || client.getNetworkHandler() == null) { pendingReply = null; return; }

        client.getNetworkHandler().sendChatMessage(pendingReply);
        pendingReply = null;
    }

    @Override
    public void onDisable() {
        pendingReply = null;
        replyDelay   = 0;
    }

    /** Add or update a trigger rule at runtime. */
    public void addRule(String trigger, String reply) {
        rules.put(trigger.toLowerCase(), reply);
    }

    public void removeRule(String trigger) {
        rules.remove(trigger.toLowerCase());
    }

    public Map<String, String> getRules() { return rules; }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
