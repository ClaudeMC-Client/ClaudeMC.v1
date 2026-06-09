package com.claudemc.account;

import com.claudemc.ClaudeMCMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/**
 * Alt account manager.
 *
 * Supports two account modes:
 *
 *  OFFLINE  — sets the client session to an offline/cracked session with any username.
 *             Works on cracked servers and offline-mode servers.
 *             UUID is generated deterministically from the username (same as vanilla offline).
 *
 *  SESSION  — uses a pre-obtained session token (accessToken + UUID).
 *             Lets you switch between real Microsoft accounts without restarting.
 *             Obtain tokens via external auth tools; paste them in here.
 *
 * Saved to .minecraft/config/claudemc/alts.json. Session accessTokens are encrypted at rest
 * (AES-256-GCM) with a locally generated key kept in a sibling file, and both files are
 * restricted to owner-only permissions where the OS supports it.
 *
 * NOTE: This protects tokens against casual disk access, backups, other user accounts, and
 * naive scrapers. It cannot fully defend against malware already running as the same OS user,
 * which can read the key file too — defeating that requires an OS keystore (DPAPI/Keychain/
 * libsecret). Treat session tokens as sensitive regardless.
 */
public class AltManager {

    public static final AltManager INSTANCE = new AltManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/alts.json");
    private static final Path KEY_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/.alts.key");
    private static final Type LIST_TYPE = new TypeToken<List<AltEntry>>() {}.getType();

    // AES-GCM at-rest encryption for session tokens.
    private static final String ENC_PREFIX = "enc:v1:"; // marks an encrypted token field
    private static final int    GCM_TAG_BITS = 128;
    private static final int    GCM_IV_BYTES = 12;
    private static final SecureRandom RNG = new SecureRandom();

    public enum AltType { OFFLINE, SESSION }

    public static class AltEntry {
        public String  name;
        public AltType type;
        public String  uuid;         // for SESSION type
        public String  accessToken;  // for SESSION type
        public AltEntry() {}
        public AltEntry(String name, AltType type, String uuid, String accessToken) {
            this.name = name; this.type = type;
            this.uuid = uuid; this.accessToken = accessToken;
        }
    }

    private final List<AltEntry> alts = new ArrayList<>();
    private String  originalName  = null;
    private Session originalSession = null;

    private AltManager() {}

    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            List<AltEntry> loaded = GSON.fromJson(r, LIST_TYPE);
            if (loaded != null) {
                for (AltEntry a : loaded) a.accessToken = decryptToken(a.accessToken);
                alts.clear();
                alts.addAll(loaded);
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AltManager] Load failed: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            // Serialise a copy with tokens encrypted; never write plaintext tokens to disk.
            List<AltEntry> encrypted = new ArrayList<>(alts.size());
            for (AltEntry a : alts) {
                encrypted.add(new AltEntry(a.name, a.type, a.uuid, encryptToken(a.accessToken)));
            }
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) { GSON.toJson(encrypted, w); }
            restrictToOwner(CONFIG_PATH);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AltManager] Save failed: {}", e.getMessage());
        }
    }

    // ── At-rest token encryption ──────────────────────────────────────────

    private static String encryptToken(String plain) {
        if (plain == null || plain.isBlank()) return plain;
        if (plain.startsWith(ENC_PREFIX)) return plain; // already encrypted
        try {
            SecretKey key = localKey();
            byte[] iv = new byte[GCM_IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // If encryption is impossible, fail closed: drop the token rather than store plaintext.
            ClaudeMCMod.LOGGER.warn("[AltManager] Token encryption failed, token not persisted: {}", e.getMessage());
            return "";
        }
    }

    private static String decryptToken(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        if (!stored.startsWith(ENC_PREFIX)) return stored; // legacy plaintext — read as-is
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv  = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
            byte[] ct  = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, localKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AltManager] Token decryption failed (wrong/missing key?): {}", e.getMessage());
            return "";
        }
    }

    /** Loads the local AES key, generating and persisting one (owner-only) on first use. */
    private static synchronized SecretKey localKey() throws Exception {
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

    /** Best-effort owner-only file permissions (POSIX rw-------; no-op on filesystems without it). */
    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX (e.g. Windows) — falls back to default user-profile ACLs.
        }
    }

    public List<AltEntry> getAlts() { return alts; }

    public void addOffline(String username) {
        alts.add(new AltEntry(username, AltType.OFFLINE, offlineUuid(username), ""));
        save();
    }

    public void addSession(String username, String uuid, String accessToken) {
        alts.add(new AltEntry(username, AltType.SESSION, uuid, accessToken));
        save();
    }

    public void remove(int index) {
        if (index >= 0 && index < alts.size()) { alts.remove(index); save(); }
    }

    /** Switch the active Minecraft session to the given alt. */
    public boolean switchTo(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (index < 0 || index >= alts.size()) return false;

        // Store original session on first switch
        if (originalSession == null) {
            originalSession = client.getSession();
            originalName    = originalSession.getUsername();
        }

        AltEntry alt = alts.get(index);
        try {
            Session newSession = switch (alt.type) {
                // 1.21.x Session no longer carries an AccountType.
                case OFFLINE -> new Session(
                    alt.name,
                    com.mojang.util.UndashedUuid.fromStringLenient(offlineUuid(alt.name)),
                    "",                          // empty token = offline/cracked
                    Optional.empty(),
                    Optional.empty()
                );
                case SESSION -> new Session(
                    alt.name,
                    com.mojang.util.UndashedUuid.fromStringLenient(alt.uuid),
                    alt.accessToken,
                    Optional.empty(),
                    Optional.empty()
                );
            };

            applySession(client, newSession);

            ClaudeMCMod.LOGGER.info("[AltManager] Switched to: {} ({})", alt.name, alt.type);
            return true;
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AltManager] Switch failed: {}", e.getMessage());
            return false;
        }
    }

    /** Restore the original session. */
    public boolean restore() {
        if (originalSession == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            applySession(client, originalSession);
            originalSession = null;
            ClaudeMCMod.LOGGER.info("[AltManager] Restored original session: {}", originalName);
            return true;
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AltManager] Restore failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Swaps the client session. Prefers the mixin accessor (remap-safe in production);
     * falls back to reflective field names if the accessor is unavailable for any reason.
     */
    private static void applySession(MinecraftClient client, Session session) throws Exception {
        try {
            ((com.claudemc.mixin.MinecraftClientAccessor) (Object) client).claudemc$setSession(session);
            return;
        } catch (Throwable accessorFailed) {
            // Fall through to reflection with both yarn and intermediary names
        }
        for (String name : new String[]{"session", "field_1726"}) {
            try {
                var field = MinecraftClient.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(client, session);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException("MinecraftClient.session (tried accessor + reflection)");
    }

    public String getActiveUsername() {
        var session = MinecraftClient.getInstance().getSession();
        return session != null ? session.getUsername() : "Unknown";
    }

    public boolean isUsingAlt() { return originalSession != null; }

    /** Deterministic offline UUID — same algorithm as Minecraft's offline player UUID. */
    private static String offlineUuid(String username) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }
}
