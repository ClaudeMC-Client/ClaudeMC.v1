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
 * Queries mcscans.fi for live Minecraft servers and filters by vulnerability / P2W status.
 *
 * Modes:
 *   Both       — vulnerability + P2W scan on all live servers
 *   Vulnerable — VulnDb match on software/motd
 *   P2W        — match against known predatory server list
 *   AI Search  — AI interprets the Query setting, queries mcscans.fi with targeted
 *                software/keyword filters, and reports matching live servers.
 *                Set Query in config/claudemc/modules.json (e.g. "BookDupe" or "AuthMe bypass").
 */
public class ServerFinder extends Module {

    public static ServerFinder INSTANCE;

    // live=true  — only servers pinged in the last 5 minutes
    // sort=player — highest population first
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

    // Software names known to be unpatched / inherently vulnerable
    private static final Set<String> VULN_SOFTWARE = Set.of(
        "spigot", "craftbukkit", "bungeecord", "waterfall", "mohist", "magma"
    );

    public ServerFinder() {
        super("ServerFinder",
              "Scans mcscans.fi for live vulnerable / P2W servers. Results in local chat.",
              Category.MISC);
        addMode("Mode",        "Both",  "Both", "Vulnerable", "P2W", "AI Search");
        addBool("UseAI",       true);
        addBool("OfflineOnly", false);   // restrict to offline-mode (cracked) servers
        addNumber("MaxResults", 20, 5, 100, 5, true);
        // Free-text query for AI Search mode — edit via config/claudemc/modules.json
        // Example values: "BookDupe", "AuthMe bypass", "BungeeCord ForceOP"
        addSetting("Query", "");
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
        String mode = getSetting("Mode");
        if (mode.equals("AI Search")) {
            client.player.sendMessage(Text.literal("§6[ServerFinder] §7AI targeted search…"), false);
        } else {
            client.player.sendMessage(Text.literal("§6[ServerFinder] §7Scanning mcscans.fi…"), false);
        }
        Thread.ofVirtual().start(() -> {
            if (getSetting("Mode").equals("AI Search")) aiTargetedScan(client);
            else                                         scan(client);
        });
    }

    // ── Standard scan pipeline ─────────────────────────────────────────────

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

    // ── AI targeted server search ─────────────────────────────────────────
    //
    // Interprets Query (e.g. "BookDupe", "AuthMe bypass", "BungeeCord ForceOP"),
    // asks the AI what software/keyword to filter on, queries MCScans with those
    // filters, and shows matching live servers.
    //
    // Falls back to VulnDb string matching if no AI key is configured.

    private void aiTargetedScan(MinecraftClient client) {
        try {
            String query = getSetting("Query").trim();
            if (query.isBlank()) {
                msg(client, "§c[ServerFinder] §7Set Query in config/claudemc/modules.json first.");
                msg(client, "§7  Example: \"BookDupe\", \"AuthMe bypass\", \"BungeeCord ForceOP\"");
                return;
            }

            msg(client, "§6[ServerFinder] §7Target: §f" + query);

            if (!AIConfig.INSTANCE.isConfigured()) {
                vulnDbTargeted(query, client);
                return;
            }

            String saved = AIConfig.INSTANCE.systemPrompt;
            AIConfig.INSTANCE.systemPrompt =
                "Minecraft exploit researcher. Respond ONLY in this format, nothing else: " +
                "software:<CSV names>|keyword:<one word>|summary:<50 words max>";

            AIClient.INSTANCE.ask(
                "Minecraft exploit or vulnerability: \"" + query + "\"\n" +
                "1. Which server software is affected? Choose from: Spigot, CraftBukkit, Paper, " +
                "BungeeCord, Waterfall, Fabric, Forge, Mohist, Velocity. " +
                "Use multiple names separated by commas if applicable. Use 'any' if it applies " +
                "to any server regardless of software.\n" +
                "2. What single keyword is most likely to appear in an affected server's MOTD " +
                "or version string? (e.g. the plugin name, server network name, or version tag)\n" +
                "3. Describe the vulnerability in 50 words or less.",
                resp -> {
                    AIConfig.INSTANCE.systemPrompt = saved;
                    handleTargetedResponse(resp, query, client);
                },
                err -> {
                    AIConfig.INSTANCE.systemPrompt = saved;
                    msg(client, "§6[ServerFinder] §7AI unavailable, falling back to VulnDb.");
                    vulnDbTargeted(query, client);
                }
            );
        } catch (Exception e) {
            msg(client, "§c[ServerFinder] Error: " + e.getMessage());
            running.set(false);
        }
    }

    private void handleTargetedResponse(String resp, String query, MinecraftClient client) {
        try {
            // Parse: software:<CSV>|keyword:<word>|summary:<text>
            String software = "", keyword = "", summary = resp.trim();
            for (String part : resp.split("\\|")) {
                int colon = part.indexOf(':');
                if (colon < 0) continue;
                String k = part.substring(0, colon).trim().toLowerCase();
                String v = part.substring(colon + 1).trim();
                switch (k) {
                    case "software" -> software = v;
                    case "keyword"  -> keyword  = v;
                    case "summary"  -> summary  = v;
                }
            }

            msg(client, "§e[ServerFinder] §7" + summary);

            // Build software list for MCScans queries (up to 3 to stay within rate limits)
            List<String> softwareList;
            if (software.isBlank() || software.equalsIgnoreCase("any")) {
                softwareList = List.of("Spigot", "CraftBukkit");
            } else {
                softwareList = Arrays.stream(software.split(","))
                    .map(String::trim).filter(s -> !s.isBlank())
                    .limit(3).collect(Collectors.toList());
            }

            final String kw = keyword.toLowerCase();
            int max = parseInt(getSetting("MaxResults"), 20);
            List<ServerEntry> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            boolean offlineOnly = Boolean.parseBoolean(getSetting("OfflineOnly"));
            String extra = offlineOnly ? "&authMode=Offline" : "";

            for (String sw : softwareList) {
                String json = fetch(API_BASE + "&software=" + sw + extra);
                if (json == null) continue;
                for (ServerEntry s : parseServers(json)) {
                    if (!seen.add(s.ip)) continue; // deduplicate
                    // If AI gave a keyword, filter by it; otherwise accept all
                    if (!kw.isBlank()) {
                        String combined = (s.motd + " " + s.version + " " + s.software).toLowerCase();
                        if (!combined.contains(kw)) continue;
                    }
                    found.add(s);
                    if (found.size() >= max * 2) break;
                }
                if (softwareList.size() > 1) {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                }
            }

            if (found.isEmpty()) {
                msg(client, "§6[ServerFinder] §7No live servers found matching '§f" + query + "§7'.");
                msg(client, "§7  Tried software: " + String.join(", ", softwareList)
                    + (kw.isBlank() ? "" : " | keyword: " + kw));
            } else {
                msg(client, "§c§l[ServerFinder] " + Math.min(found.size(), max)
                    + " live server" + (found.size() == 1 ? "" : "s") + " matching '" + query + "':");
                for (ServerEntry s : found.subList(0, Math.min(found.size(), max))) {
                    msg(client, "§c  " + s.ip + ":" + s.port
                        + " §7| " + s.softwareLabel() + " | " + s.onlinePlayers + "p | §e" + s.motd);
                }
            }
        } catch (Exception e) {
            msg(client, "§c[ServerFinder] Targeted scan error: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** Fallback when no AI key: searches VulnDb for matching entries, then queries MCScans. */
    private void vulnDbTargeted(String query, MinecraftClient client) {
        try {
            String lq = query.toLowerCase();
            List<VulnDb.VulnEntry> matches = VulnDb.all().stream()
                .filter(v -> v.severity() != VulnDb.Severity.PATCHED
                    && (v.pluginName().toLowerCase().contains(lq)
                        || v.description().toLowerCase().contains(lq)))
                .collect(Collectors.toList());

            if (matches.isEmpty()) {
                msg(client, "§6[ServerFinder] §7No VulnDb entries match '§f" + query + "§7'.");
                msg(client, "§7  Configure an AI key in §f[AI]§7 for smarter server search.");
                return;
            }

            int max = parseInt(getSetting("MaxResults"), 20);
            boolean offlineOnly = Boolean.parseBoolean(getSetting("OfflineOnly"));

            for (VulnDb.VulnEntry v : matches.subList(0, Math.min(3, matches.size()))) {
                msg(client, "§e[ServerFinder] §7VulnDb: §c" + v.pluginName()
                    + " §7[" + v.severity() + "]: "
                    + v.description().substring(0, Math.min(80, v.description().length())));

                String sw = inferSoftwareFromVulnEntry(v);
                String url = API_BASE + "&software=" + sw + (offlineOnly ? "&authMode=Offline" : "");
                String json = fetch(url);
                if (json == null) continue;

                List<ServerEntry> servers = parseServers(json);
                // Filter by plugin name appearing in motd/version
                String kw = v.pluginName().toLowerCase();
                List<ServerEntry> filtered = servers.stream()
                    .filter(s -> (s.motd + " " + s.version).toLowerCase().contains(kw)
                              || s.software.equalsIgnoreCase(v.pluginName()))
                    .limit(max)
                    .collect(Collectors.toList());

                if (filtered.isEmpty() && !servers.isEmpty()) {
                    // Show all results if keyword filter yields nothing
                    filtered = servers.subList(0, Math.min(servers.size(), max));
                }

                if (!filtered.isEmpty()) {
                    msg(client, "§c  Live §f" + sw + "§c servers: " + filtered.size());
                    for (ServerEntry s : filtered) {
                        msg(client, "§c    " + s.ip + ":" + s.port
                            + " §7| " + s.softwareLabel() + " | " + s.onlinePlayers + "p");
                    }
                }
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            }
        } catch (Exception e) {
            msg(client, "§c[ServerFinder] VulnDb search error: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** Infers the most suitable MCScans software filter from a VulnEntry. */
    private String inferSoftwareFromVulnEntry(VulnDb.VulnEntry v) {
        String name = v.pluginName().toLowerCase();
        if (name.contains("bungeecord") || name.contains("waterfall")) return "BungeeCord";
        if (name.contains("paper") || name.contains("purpur"))         return "Paper";
        if (name.contains("craftbukkit"))                               return "CraftBukkit";
        if (name.contains("fabric"))                                    return "Fabric";
        if (name.contains("forge") || name.contains("mohist"))         return "Forge";
        // Plugin-specific entries default to Spigot (most common plugin host)
        return "Spigot";
    }

    // ── Vulnerability matching ────────────────────────────────────────────

    private List<ServerEntry> findVulnerable(List<ServerEntry> servers, int max) {
        List<ServerEntry> out = new ArrayList<>();
        for (ServerEntry s : servers) {
            if (out.size() >= max) break;

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
    // MCScans API: { "totalServers": N, "servers": [ { "hostname":..., "software":..., ... } ] }

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

                if (o.has("playerStats") && o.get("playerStats").isJsonObject()) {
                    JsonObject ps = o.getAsJsonObject("playerStats");
                    if (ps.has("onlinePlayers") && !ps.get("onlinePlayers").isJsonNull())
                        e.onlinePlayers = ps.get("onlinePlayers").getAsInt();
                }

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

        String softwareLabel() {
            String sw = software.isBlank() ? "" : software + " ";
            return (sw + version).trim();
        }
    }
}
