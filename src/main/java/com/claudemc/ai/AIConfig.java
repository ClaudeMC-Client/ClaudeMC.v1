package com.claudemc.ai;

import com.claudemc.ClaudeMCMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import com.google.gson.JsonObject;
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

    private static final Path KEY_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/.ai.key");

    private static final String ENC_PREFIX   = "enc:v1:";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    GCM_IV_BYTES = 12;
    private static final SecureRandom AI_RNG  = new SecureRandom();

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
            INSTANCE.anthropicKey = decryptKey(orDefault(loaded.anthropicKey, ""));
            INSTANCE.openaiKey    = decryptKey(orDefault(loaded.openaiKey, ""));
            INSTANCE.geminiKey    = decryptKey(orDefault(loaded.geminiKey, ""));
            INSTANCE.model        = orDefault(loaded.model, "");
            INSTANCE.maxTokens    = loaded.maxTokens > 0 ? loaded.maxTokens : 300;
            INSTANCE.systemPrompt = orDefault(loaded.systemPrompt, INSTANCE.systemPrompt);
            INSTANCE.shodanApiKey = decryptKey(orDefault(loaded.shodanApiKey, ""));
            INSTANCE.censysApiKey = decryptKey(orDefault(loaded.censysApiKey, ""));
            INSTANCE.fofaApiKey   = decryptKey(orDefault(loaded.fofaApiKey,   ""));
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIConfig] Load failed: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            // Serialize with all key fields AES-GCM-encrypted; never write plaintext
            JsonObject out = new JsonObject();
            out.addProperty("provider",     INSTANCE.provider);
            out.addProperty("anthropicKey", encryptKey(INSTANCE.anthropicKey));
            out.addProperty("openaiKey",    encryptKey(INSTANCE.openaiKey));
            out.addProperty("geminiKey",    encryptKey(INSTANCE.geminiKey));
            out.addProperty("shodanApiKey", encryptKey(INSTANCE.shodanApiKey));
            out.addProperty("censysApiKey", encryptKey(INSTANCE.censysApiKey));
            out.addProperty("fofaApiKey",   encryptKey(INSTANCE.fofaApiKey));
            out.addProperty("model",        INSTANCE.model);
            out.addProperty("maxTokens",    INSTANCE.maxTokens);
            out.addProperty("systemPrompt", INSTANCE.systemPrompt);
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) { GSON.toJson(out, w); }
            restrictToOwner(CONFIG_PATH);
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

    private static String encryptKey(String plain) {
        if (plain == null || plain.isBlank()) return plain;
        if (plain.startsWith(ENC_PREFIX)) return plain;
        try {
            SecretKey key = aiLocalKey();
            byte[] iv = new byte[GCM_IV_BYTES];
            AI_RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIConfig] Key encryption failed, key not persisted: {}", e.getMessage());
            return "";
        }
    }

    private static String decryptKey(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        if (!stored.startsWith(ENC_PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv  = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
            byte[] ct  = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aiLocalKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIConfig] Key decryption failed (wrong/missing key?): {}", e.getMessage());
            return "";
        }
    }

    private static synchronized SecretKey aiLocalKey() throws Exception {
        if (Files.exists(KEY_PATH)) {
            byte[] raw = Base64.getDecoder().decode(Files.readString(KEY_PATH).trim());
            return new SecretKeySpec(raw, "AES");
        }
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey key = kg.generateKey();
        Files.createDirectories(KEY_PATH.getParent());
        Files.writeString(KEY_PATH, Base64.getEncoder().encodeToString(key.getEncoded()));
        restrictToOwner(KEY_PATH);
        return key;
    }

    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {}
    }

    private static String orDefault(String v, String def) {
        return (v == null) ? def : v;
    }
}
