package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.ai.WebSearch;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.server.VulnDb;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Queries mcscans.fi for live Minecraft servers and filters by vulnerability and/or P2W status.
 *
 * Uses the real MCScans API response format:
 *   hostname, port, version, software, motd, playerStats.onlinePlayers, authMode
 *
 * API params used:
 *   live=true      — only servers seen in the last 5 minutes
 *   sort=player    — highest online population first (more interesting targets)
 *   authMode=Offline — optional, when OfflineOnly=true (cracked servers, no MS auth)
 */
public class ServerFinder extends Module {

    public static ServerFinder INSTANCE;

    private static final String API_BASE =
        "https://api.mcscans.fi/public/v1/servers?live=true&sort=player";

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── Known P2W / child-gambling servers ────────────────────────────────
    private static final Set<String> KNOWN_P2W = Set.of(
        "cosmicpvp", "mineclex", "manacube", "purpleprison", "gotpvp",
        "opfactions", "ultraprison", "mineland", "hypixel",
        "cubovation", "veltpvp", "mineplex", "cubecraft",
        "playlegend", "justmysocks", "jackpotmc", "lemoncloud",
        "skyblock.net", "moxmc", "minecraftprison",
        "foxcraft", "minetime", "orionmc", "galaxyminecraft",
        "extradragons", "opcraft", "skyblock.games", "zeqa"
    );

    // Server software that is inherently unpatched / known-vulnerable
    private static final Set<String> VULN_SOFTWARE = Set.of(
        "spigot", "craftbukkit", "bungeecord", "waterfall", "mohist", "magma"
    );

    public ServerFinder() {
        super("ServerFinder",
              "Scans mcscans.fi for live vulnerable / P2W servers. Results in local chat.",
              Category.MISC);
        addMode("Mode",        "Both",  "Both", "Vulnerable", "P2W");
        addBool("UseAI",       true);
        addBool("OfflineOnly", false);   // restrict to offline-mode (cracked) servers
        addNumber("MaxResults", 20, 5, 100, 5, true);
        INSTANCE = this;
    }

    @Override public void onTick(MinecraftClient client) {}
    @Override public void onDisable() { running.set(false); }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!running.compareAndSet(false, true)) {
            client.player.sendMessage(Text.literal("§e[ServerFinder] §7Scan already running."), false);
            return;
        }
        client.player.sendMessage(Text.literal("§6[ServerFinder] §7Scanning mcscans.fi…"), false);
        Thread.ofVirtual().start(() -> scan(client));
    }

    // ── Scan pipeline ─────────────────────────────────────────────────────

    private void scan(MinecraftClient client) {
        try {
            boolean offlineOnly = Boolean.parseBoolean(getSetting("OfflineOnly"));
            String url = API_BASE + (offlineOnly ? "&authMode=Offline" : "");

            String json = fetch(url);
            if (json == null) {
                msg(client, "§c[ServerFinder] §7Failed to reach mcscans.fi"); return;
            }

            List<ServerEntry> servers = parseServers(json);
            if (servers.isEmpty()) {
                msg(client, "§6[ServerFinder] §7No live servers returned."); return;
            }

            int    max  = parseInt(getSetting("MaxResults"), 20);
            String mode = getSetting("Mode");

            // Collect both lists upfront for cross-referencing
            List<ServerEntry> vulnList = findVulnerable(servers, max);
            List<ServerEntry> p2wList  = findP2W(servers, max);
            Set<String> vulnIps = vulnList.stream().map(s -> s.ip).collect(Collectors.toSet());
            Set<String> p2wIps  = p2wList.stream().map(s -> s.ip).collect(Collectors.toSet());

            if (mode.equals("Both") || mode.equals("Vulnerable")) {
                if (vulnList.isEmpty()) {
                    msg(client, "§6[ServerFinder] §7No clearly vulnerable servers in this page.");
                } else {
                    msg(client, "§c§l[ServerFinder] Vulnerable (" + vulnList.size() + "):");
                    for (ServerEntry s : vulnList) {
                        String p2wTag = p2wIps.contains(s.ip) ? " §d§l[⚠ ALSO P2W]" : "";
                        msg(client, "§c  " + s.ip + ":" + s.port + p2wTag
                            + " §7| " + s.softwareLabel() + " | " + s.onlinePlayers + "p"
                            + " | §e" + s.vulnSummary);
                    }
                }
            }

            if (mode.equals("Both") || mode.equals("P2W")) {
                msg(client, "§d§l[ServerFinder] P2W / gambling (" + p2wList.size() + "):");
                for (ServerEntry s : p2wList) {
                    String vulnTag = vulnIps.contains(s.ip) ? " §c§l[⚠ EXPLOITABLE]" : "";
                    msg(client, "§d  " + s.ip + ":" + s.port + vulnTag
                        + " §7| " + s.onlinePlayers + "p | §e" + s.motd);
                }
                if (Boolean.parseBoolean(getSetting("UseAI")) && AIConfig.INSTANCE.isConfigured()) {
                    aiP2WList(client);
                } else {
                    webP2WList(client);
                }
            }
        } catch (Exception e) {
            msg(client, "§c[ServerFinder] Error: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    // ── Vulnerability matching ────────────────────────────────────────────

    private List<ServerEntry> findVulnerable(List<ServerEntry> servers, int max) {
        List<ServerEntry> out = new ArrayList<>();
        for (ServerEntry s : servers) {
            if (out.size() >= max) break;

            // Search VulnDb — check software name first (direct), then version+motd
            String combined = (s.software + " " + s.version + " " + s.motd).toLowerCase();
            for (VulnDb.VulnEntry v : VulnDb.all()) {
                if (v.severity() == VulnDb.Severity.PATCHED) continue;
                if (combined.contains(v.pluginName().toLowerCase())) {
                    s.vulnSummary = v.severity() + ": " + v.pluginName()
                        + " — " + v.description().substring(0, Math.min(60, v.description().length()));
                    out.add(s);
                    break;
                }
            }

            // Catch unpatched server software not yet in VulnDb
            if (s.vulnSummary.isEmpty() && VULN_SOFTWARE.contains(s.software.toLowerCase())) {
                s.vulnSummary = "Unpatched server software: " + s.software;
                out.add(s);
            }
        }
        return out;
    }

    // ── P2W matching ──────────────────────────────────────────────────────

    private List<ServerEntry> findP2W(List<ServerEntry> servers, int max) {
        List<ServerEntry> out = new ArrayList<>();
        for (ServerEntry s : servers) {
            if (out.size() >= max) break;
            String key = (s.ip + " " + s.motd).toLowerCase();
            for (String p2w : KNOWN_P2W) {
                if (key.contains(p2w)) { out.add(s); break; }
            }
        }
        return out;
    }

    private void webP2WList(MinecraftClient client) {
        List<String> snippets = WebSearch.search(
            "minecraft pay to win child gambling servers list 2024 2025 crate keys p2w", 6);
        if (!snippets.isEmpty()) {
            msg(client, "§d[ServerFinder] §7Web — known P2W ecosystems:");
            for (String s : snippets)
                msg(client, "§7  • " + s.substring(0, Math.min(120, s.length())));
        }
    }

    private void aiP2WList(MinecraftClient client) {
        List<String> snippets = WebSearch.search(
            "minecraft pay to win child gambling servers list crate keys 2024 2025 p2w report", 8);
        String webCtx = snippets.isEmpty() ? "" :
            "\nWeb results:\n" + String.join("\n", snippets.subList(0, Math.min(4, snippets.size())));

        String saved = AIConfig.INSTANCE.systemPrompt;
        AIConfig.INSTANCE.systemPrompt =
            "You list Minecraft servers. Be concise. Output: numbered list only, format: ServerName | IP (if known) | Why P2W";
        AIClient.INSTANCE.ask(
            "List up to 15 well-known Minecraft servers that are pay-to-win or have child gambling "
            + "mechanics (crate keys, OP spawners for sale, /fly for pay, in-game currency sales). "
            + "Focus on servers still active in 2024-2025." + webCtx,
            resp -> {
                AIConfig.INSTANCE.systemPrompt = saved;
                msg(client, "§d§l[ServerFinder] AI P2W/gambling list:");
                for (String line : resp.split("\n")) {
                    String t = line.trim();
                    if (!t.isBlank()) msg(client, "§d  " + t);
                }
            },
            err -> { AIConfig.INSTANCE.systemPrompt = saved; webP2WList(client); }
        );
    }

    // ── JSON parsing ─────────────────────────────────────────────────────
    // MCScans API response: { "totalServers": N, "servers": [ { "hostname": ..., ... } ] }

    private List<ServerEntry> parseServers(String json) {
        List<ServerEntry> out = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            JsonArray  arr  = root.has("servers")
                              ? root.getAsJsonArray("servers") : new JsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();

                String hostname = str(o, "hostname");
                if (hostname == null || hostname.isBlank()) continue;

                ServerEntry e = new ServerEntry();
                e.ip       = hostname;
                e.port     = o.has("port") && !o.get("port").isJsonNull()
                             ? String.valueOf(o.get("port").getAsInt()) : "25565";
                e.version  = orEmpty(str(o, "version"));
                e.software = orEmpty(str(o, "software"));
                e.motd     = orEmpty(str(o, "motd")).replaceAll("§.", "").trim();

                // Nested playerStats object
                if (o.has("playerStats") && o.get("playerStats").isJsonObject()) {
                    JsonObject ps = o.getAsJsonObject("playerStats");
                    if (ps.has("onlinePlayers") && !ps.get("onlinePlayers").isJsonNull())
                        e.onlinePlayers = ps.get("onlinePlayers").getAsInt();
                }

                // authMode: 0=offline/cracked, 1=online, 2=whitelist
                if (o.has("authMode") && !o.get("authMode").isJsonNull()
                        && o.get("authMode").isJsonPrimitive())
                    e.authMode = o.get("authMode").getAsInt();

                out.add(e);
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerFinder] Parse error: {}", e.getMessage());
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }

    // ── HTTP ──────────────────────────────────────────────────────────────

    private String fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept",     "application/json")
                .header("User-Agent", "ClaudeMC/1.17")
                .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ServerFinder] Fetch failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void msg(MinecraftClient client, String s) {
        if (client.player != null) client.player.sendMessage(Text.literal(s), false);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static class ServerEntry {
        String ip, port = "25565", version = "", software = "", motd = "", vulnSummary = "";
        int    onlinePlayers = 0;
        int    authMode      = -1; // -1=unknown, 0=offline, 1=online, 2=whitelist

        /** Short label like "Spigot 1.20.4" or just "1.20.4" if no software reported. */
        String softwareLabel() {
            String sw = software.isBlank() ? "" : software + " ";
            return (sw + version).trim();
        }
    }
}
