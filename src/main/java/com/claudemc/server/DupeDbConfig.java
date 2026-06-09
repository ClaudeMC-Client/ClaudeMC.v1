package com.claudemc.server;

import com.claudemc.ClaudeMCMod;
import com.google.gson.*;
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
import java.io.*;
import java.nio.file.*;

/**
 * Persists DupeDB OAuth tokens and app settings.
 * Stored at .minecraft/config/claudemc/dupedb.json.
 *
 * The appId must match an app registered at https://dupedb.net (account settings → OAuth apps).
 * Register the loopback redirect URI: http://127.0.0.1/callback
 */
public class DupeDbConfig {

    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/dupedb.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path KEY_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/.dupedb.key");

    private static final String ENC_PREFIX   = "enc:v1:";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    GCM_IV_BYTES = 12;
    private static final SecureRandom DD_RNG  = new SecureRandom();

    public String appId         = "claudemc";
    public String accessToken   = "";
    public String refreshToken  = "";
    public long   tokenExpiresAt = 0; // epoch seconds

    public boolean hasToken() {
        return !accessToken.isBlank();
    }

    public static DupeDbConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    DupeDbConfig cfg = GSON.fromJson(r, DupeDbConfig.class);
                    if (cfg != null) {
                        cfg.accessToken  = decryptToken(cfg.accessToken);
                        cfg.refreshToken = decryptToken(cfg.refreshToken);
                        return cfg;
                    }
                }
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Config load failed: {}", e.getMessage());
        }
        return new DupeDbConfig();
    }

    public static void save(DupeDbConfig cfg) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            DupeDbConfig enc = new DupeDbConfig();
            enc.appId          = cfg.appId;
            enc.accessToken    = encryptToken(cfg.accessToken);
            enc.refreshToken   = encryptToken(cfg.refreshToken);
            enc.tokenExpiresAt = cfg.tokenExpiresAt;
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) { GSON.toJson(enc, w); }
            restrictToOwner(CONFIG_PATH);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Config save failed: {}", e.getMessage());
        }
    }

    private static String encryptToken(String plain) {
        if (plain == null || plain.isBlank()) return plain;
        if (plain.startsWith(ENC_PREFIX)) return plain;
        try {
            SecretKey key = ddLocalKey();
            byte[] iv = new byte[GCM_IV_BYTES];
            DD_RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Token encryption failed, token not persisted: {}", e.getMessage());
            return "";
        }
    }

    private static String decryptToken(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        if (!stored.startsWith(ENC_PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv  = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
            byte[] ct  = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, ddLocalKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Token decryption failed: {}", e.getMessage());
            return "";
        }
    }

    private static synchronized SecretKey ddLocalKey() throws Exception {
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
}
