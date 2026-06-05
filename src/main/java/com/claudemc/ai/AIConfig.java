package com.claudemc.ai;

import com.claudemc.ClaudeMCMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

/**
 * Persists API keys and provider preferences.
 * Stored at .minecraft/config/claudemc/ai.json — never committed anywhere.
 */
public class AIConfig {

    public static final AIConfig INSTANCE = new AIConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/ai.json");

    // ── Fields saved to disk ─────────────────────────────────────────────

    /** Which provider to use: "anthropic", "openai", "gemini" */
    public String  provider     = "anthropic";

    public String  anthropicKey = "";
    public String  openaiKey    = "";
    public String  geminiKey    = "";

    /** Empty = use provider default model */
    public String  model        = "";

    public int     maxTokens    = 300;

    /** System-prompt prefix prepended to all requests */
    public String  systemPrompt = "You are an assistant integrated into a Minecraft client mod. Be brief (1-3 sentences max). No markdown.";

    /** Shodan API key for server discovery (optional). Get one at account.shodan.io */
    public String  shodanApiKey = "";

    /** Censys Personal Access Token for host search (optional). Get one at app.censys.io/account/api */
    public String  censysApiKey = "";

    /** FOFA API key for server discovery (optional). Get one at fofa.info/user/info */
    public String  fofaApiKey   = "";

    private AIConfig() {}

    // ── Persistence ──────────────────────────────────────────────────────

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            AIConfig loaded = GSON.fromJson(r, AIConfig.class);
            if (loaded == null) return;
            INSTANCE.provider     = orDefault(loaded.provider, "anthropic");
            INSTANCE.anthropicKey = orDefault(loaded.anthropicKey, "");
            INSTANCE.openaiKey    = orDefault(loaded.openaiKey, "");
            INSTANCE.geminiKey    = orDefault(loaded.geminiKey, "");
            INSTANCE.model        = orDefault(loaded.model, "");
            INSTANCE.maxTokens    = loaded.maxTokens > 0 ? loaded.maxTokens : 300;
            INSTANCE.systemPrompt = orDefault(loaded.systemPrompt, INSTANCE.systemPrompt);
            INSTANCE.shodanApiKey = orDefault(loaded.shodanApiKey, "");
            INSTANCE.censysApiKey = orDefault(loaded.censysApiKey, "");
            INSTANCE.fofaApiKey   = orDefault(loaded.fofaApiKey,   "");
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIConfig] Load failed: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIConfig] Save failed: {}", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Returns true if the active provider has a non-empty API key. */
    public boolean isConfigured() {
        return !activeKey().isBlank();
    }

    public String activeKey() {
        return switch (provider.toLowerCase()) {
            case "openai"  -> openaiKey;
            case "gemini"  -> geminiKey;
            default        -> anthropicKey;
        };
    }

    /** Default model for each provider (used when model field is empty). */
    public String resolvedModel() {
        if (!model.isBlank()) return model;
        return switch (provider.toLowerCase()) {
            case "openai"  -> "gpt-4o-mini";
            case "gemini"  -> "gemini-2.0-flash";
            default        -> "claude-haiku-4-5-20251001";
        };
    }

    private static String orDefault(String v, String def) {
        return (v == null) ? def : v;
    }
}
