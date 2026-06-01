package com.claudemc.server;

import com.claudemc.ClaudeMCMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

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
                    if (cfg != null) return cfg;
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
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(cfg, w);
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Config save failed: {}", e.getMessage());
        }
    }
}
