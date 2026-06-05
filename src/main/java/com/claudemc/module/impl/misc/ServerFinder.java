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

import java.net.InetAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Base64;
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
    // Paper/Purpur are included because older builds have known plugin-chain vulns
    private static final Set<String> VULN_SOFTWARE = Set.of(
        "spigot", "craftbukkit", "bungeecord", "waterfall", "mohist", "magma",
        "paper", "purpur", "fabric", "forge", "vanilla"
    );

    // Hosting providers whose web console panels are known to be XSS-vulnerable
    // Key: hostname substring, Value: panel type
    private static final java.util.LinkedHashMap<String, String> HOSTING_PANELS;
    static {
        HOSTING_PANELS = new java.util.LinkedHashMap<>();
        HOSTING_PANELS.put("shockbyte",            "Multicraft");
        HOSTING_PANELS.put("apexminecrafthosting", "Multicraft");
        HOSTING_PANELS.put("apexhosting",          "Multicraft");
        HOSTING_PANELS.put("bisecthosting",        "Multicraft");
        HOSTING_PANELS.put("mcprohosting",         "Multicraft");
        HOSTING_PANELS.put("serverminer",          "Multicraft");
        HOSTING_PANELS.put("ggservers",            "Multicraft");
        HOSTING_PANELS.put("craftersland",         "Multicraft");
        HOSTING_PANELS.put("minecrafthosting",     "Multicraft");
        HOSTING_PANELS.put("hosthorde",            "Multicraft");
        HOSTING_PANELS.put("nodecraft",            "NodeCraft panel (jQuery-CMD)");
        HOSTING_PANELS.put("aternos",              "Aternos web panel");
        HOSTING_PANELS.put("minehut",              "Minehut panel");
        HOSTING_PANELS.put("server.pro",           "Server.pro panel");
    }

    public ServerFinder() {
        super("ServerFinder",
              "Scans mcscans.fi for live vulnerable / P2W servers. Results in local chat.",
              Category.MISC);
        addMode("Mode",        "Both",  "Both", "Vulnerable", "P2W", "AI Search", "FOFA", "Censys");
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
        String sourceLabel = switch (mode) {
            case "AI Search" -> "AI targeted search…";
            case "FOFA"      -> "Scanning FOFA…";
            case "Censys"    -> "Scanning Censys…";
            default          -> "Scanning mcscans.fi…";
        };
        client.player.sendMessage(Text.literal("§6[ServerFinder] §7" + sourceLabel), false);
        Thread.ofVirtual().start(() -> {
            switch (getSetting("Mode")) {
                case "AI Search" -> aiTargetedScan(client);
                case "FOFA"      -> scanFofa(client);
                case "Censys"    -> scanCensys(client);
                default          -> scan(client);
            }
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

            String aiSearchSys =
                "Minecraft exploit researcher. Respond ONLY in this format, nothing else: " +
                "software:<CSV names>|keyword:<one word>|summary:<50 words max>";

            AIClient.INSTANCE.ask(aiSearchSys,
                "Minecraft exploit or vulnerability: \"" + query + "\"\n" +
                "1. Which server software is affected? Choose from: Spigot, CraftBukkit, Paper, " +
                "BungeeCord, Waterfall, Fabric, Forge, Mohist, Velocity. " +
                "Use multiple names separated by commas if applicable. Use 'any' if it applies " +
                "to any server regardless of software.\n" +
                "2. What single keyword is most likely to appear in an affected server's MOTD " +
                "or version string? (e.g. the plugin name, server network name, or version tag)\n" +
                "3. Describe the vulnerability in 50 words or less.",
                resp -> handleTargetedResponse(resp, query, client),
                err -> {
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
        // Pre-collect HIGH/CRITICAL VulnDb entries for generic software matches
        List<VulnDb.VulnEntry> highEntries = VulnDb.all().stream()
            .filter(v -> v.severity() == VulnDb.Severity.HIGH || v.severity() == VulnDb.Severity.CRITICAL)
            .collect(Collectors.toList());

        for (ServerEntry s : servers) {
            if (out.size() >= max) break;

            // 1. Direct plugin-name match in software/version/motd string
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

            // 2. Software-name match — flag unpatched server builds and show sample vuln
            if (s.vulnSummary.isEmpty() && VULN_SOFTWARE.contains(s.software.toLowerCase())) {
                // Find a relevant VulnDb entry to show (prefer CRITICAL > HIGH)
                String sample = highEntries.isEmpty() ? "" : highEntries.get(0).pluginName()
                    + "/" + (highEntries.size() > 1 ? highEntries.get(1).pluginName() : "");
                s.vulnSummary = "Unpatched " + s.software
                    + (sample.isEmpty() ? "" : " — may run " + sample + " (HIGH vuln)");
                out.add(s);
            }

            // 3. Hosting panel XSS detection via reverse DNS
            if (s.vulnSummary.isEmpty()) {
                String panel = detectHostingPanel(s.ip);
                if (panel != null) {
                    s.vulnSummary = "XSS-vulnerable web panel: " + panel + " (use WebConsoleXSS)";
                    out.add(s);
                }
            } else {
                // Append panel vuln if also on a known hosting provider
                String panel = detectHostingPanel(s.ip);
                if (panel != null) s.vulnSummary += " | §e[XSS: " + panel + "]";
            }
        }
        return out;
    }

    /** Reverse-DNS the server IP and match against known vulnerable hosting providers. */
    private String detectHostingPanel(String ip) {
        try {
            String hostname = InetAddress.getByName(ip)
                .getCanonicalHostName().toLowerCase();
            for (Map.Entry<String, String> e : HOSTING_PANELS.entrySet()) {
                if (hostname.contains(e.getKey()) || ip.toLowerCase().contains(e.getKey()))
                    return e.getValue();
            }
        } catch (Exception ignored) {}
        return null;
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

        String p2wSys =
            "You list Minecraft servers. Be concise. Output: numbered list only, format: ServerName | IP (if known) | Why P2W";
        AIClient.INSTANCE.ask(p2wSys,
            "List up to 15 well-known Minecraft servers that are pay-to-win or have child gambling "
            + "mechanics (crate keys, OP spawners for sale, /fly for pay, in-game currency sales). "
            + "Focus on servers still active in 2024-2025." + webCtx,
            resp -> {
                msg(client, "§d§l[ServerFinder] AI P2W/gambling list:");
                for (String line : resp.split("\n")) {
                    String t = line.trim();
                    if (!t.isBlank()) msg(client, "§d  " + t);
                }
            },
            err -> webP2WList(client)
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

    // ── FOFA scan ─────────────────────────────────────────────────────────
    //
    // Queries FOFA for hosts with port 25565 and minecraft protocol.
    // Requires fofaApiKey in config/claudemc/ai.json (fofa.info/user/info).

    private void scanFofa(MinecraftClient client) {
        try {
            String key = AIConfig.INSTANCE.fofaApiKey.trim();
            if (key.isBlank()) {
                msg(client, "§c[ServerFinder] §7FOFA key not set. Add fofaApiKey in §fconfig/claudemc/ai.json");
                return;
            }

            // FOFA query syntax: port="25565" && protocol="minecraft"
            String q      = "port=\"25565\" && protocol=\"minecraft\"";
            String qb64   = Base64.getEncoder().encodeToString(q.getBytes(StandardCharsets.UTF_8));
            int    max    = parseInt(getSetting("MaxResults"), 20);
            String fields = "ip,port,protocol,country,banner,host";

            String url = "https://fofa.info/api/v1/search/all"
                + "?key="     + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&qbase64=" + qb64
                + "&fields="  + fields
                + "&size="    + Math.min(max * 2, 100);

            String raw = fetch(url);
            if (raw == null) {
                msg(client, "§c[ServerFinder] §7FOFA request failed. Check key/quota.");
                return;
            }

            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root.has("errmsg")) {
                msg(client, "§c[ServerFinder] FOFA error: " + root.get("errmsg").getAsString());
                return;
            }

            JsonArray results = root.has("results") ? root.getAsJsonArray("results") : new JsonArray();
            if (results.isEmpty()) {
                msg(client, "§6[ServerFinder] §7FOFA returned no results.");
                return;
            }

            // results = [[ip, port, protocol, country, banner, host], ...]
            List<ServerEntry> servers = new ArrayList<>();
            for (JsonElement el : results) {
                if (!el.isJsonArray()) continue;
                JsonArray row = el.getAsJsonArray();
                ServerEntry e = new ServerEntry();
                e.ip       = safeStr(row, 0);
                e.port     = safeStr(row, 1).isBlank() ? "25565" : safeStr(row, 1);
                e.software = safeStr(row, 2); // protocol field
                e.motd     = safeStr(row, 4); // banner
                if (e.ip.isBlank()) continue;
                servers.add(e);
            }

            int shown = 0;
            msg(client, "§b§l[ServerFinder] FOFA: " + servers.size() + " Minecraft hosts");
            List<ServerEntry> vulnList = findVulnerable(servers, max);
            if (!vulnList.isEmpty()) {
                msg(client, "§c  Vulnerable (" + vulnList.size() + "):");
                for (ServerEntry s : vulnList) {
                    msg(client, "§c    " + s.ip + ":" + s.port + " §7| §e" + s.vulnSummary);
                    if (++shown >= max) break;
                }
            } else {
                for (ServerEntry s : servers.subList(0, Math.min(servers.size(), max))) {
                    String country = safeCountry(s);
                    msg(client, "§b  " + s.ip + ":" + s.port
                        + (country.isBlank() ? "" : " §7[" + country + "]")
                        + " §7| " + (s.motd.isBlank() ? "no banner" : s.motd.substring(0, Math.min(60, s.motd.length()))));
                }
            }
        } catch (Exception e) {
            msg(client, "§c[ServerFinder] FOFA error: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private String safeStr(JsonArray a, int idx) {
        if (idx >= a.size() || a.get(idx).isJsonNull()) return "";
        return a.get(idx).getAsString();
    }

    private String safeCountry(ServerEntry s) { return ""; } // country is index 3 in raw row

    // ── Censys scan ───────────────────────────────────────────────────────
    //
    // Queries Censys v3 for hosts with port 25565 open.
    // Requires censysApiKey (Personal Access Token) in config/claudemc/ai.json.
    // Free tier: host lookup only. Starter+: search supported.

    private void scanCensys(MinecraftClient client) {
        try {
            String pat = AIConfig.INSTANCE.censysApiKey.trim();
            if (pat.isBlank()) {
                msg(client, "§c[ServerFinder] §7Censys key not set. Add censysApiKey in §fconfig/claudemc/ai.json");
                return;
            }

            int max = parseInt(getSetting("MaxResults"), 20);

            // POST /v3/global/asset/search
            String url  = "https://api.platform.censys.io/v3/global/asset/search";
            String body = GSON.toJson(buildCensysQuery(max));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept",        "application/json")
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + pat)
                .header("User-Agent",    "ClaudeMC/1.20")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                msg(client, "§c[ServerFinder] §7Censys: auth failed (check your Personal Access Token).");
                return;
            }
            if (resp.statusCode() != 200) {
                msg(client, "§c[ServerFinder] §7Censys: HTTP " + resp.statusCode());
                return;
            }

            JsonObject root = GSON.fromJson(resp.body(), JsonObject.class);
            if (!root.has("result")) {
                msg(client, "§6[ServerFinder] §7Censys returned unexpected response.");
                return;
            }

            JsonObject result = root.getAsJsonObject("result");
            JsonArray  hits   = result.has("hits") ? result.getAsJsonArray("hits") : new JsonArray();

            if (hits.isEmpty()) {
                msg(client, "§6[ServerFinder] §7Censys returned no hosts.");
                return;
            }

            List<ServerEntry> servers = new ArrayList<>();
            for (JsonElement el : hits) {
                if (!el.isJsonObject()) continue;
                JsonObject h  = el.getAsJsonObject();
                ServerEntry e = new ServerEntry();
                e.ip = h.has("ip") ? h.get("ip").getAsString() : "";
                if (e.ip.isBlank()) continue;

                // services array — find port 25565
                if (h.has("services") && h.get("services").isJsonArray()) {
                    for (JsonElement svc : h.getAsJsonArray("services")) {
                        if (!svc.isJsonObject()) continue;
                        JsonObject sv = svc.getAsJsonObject();
                        int svcPort = sv.has("port") ? sv.get("port").getAsInt() : 0;
                        if (svcPort == 25565 || svcPort == 0) {
                            e.port = String.valueOf(svcPort == 0 ? 25565 : svcPort);
                            if (sv.has("service_name"))
                                e.software = sv.get("service_name").getAsString();
                            if (sv.has("banner"))
                                e.motd = sv.get("banner").getAsString()
                                    .replaceAll("§.", "").trim();
                            break;
                        }
                    }
                }
                servers.add(e);
            }

            msg(client, "§b§l[ServerFinder] Censys: " + servers.size() + " Minecraft hosts");
            List<ServerEntry> vulnList = findVulnerable(servers, max);
            if (!vulnList.isEmpty()) {
                msg(client, "§c  Vulnerable (" + vulnList.size() + "):");
                for (ServerEntry s : vulnList) {
                    msg(client, "§c    " + s.ip + ":" + s.port + " §7| §e" + s.vulnSummary);
                }
            } else {
                for (ServerEntry s : servers.subList(0, Math.min(servers.size(), max))) {
                    msg(client, "§b  " + s.ip + ":" + s.port
                        + (s.software.isBlank() ? "" : " §7| " + s.software)
                        + (s.motd.isBlank()     ? "" : " §7| " + s.motd.substring(0, Math.min(60, s.motd.length()))));
                }
            }
        } catch (Exception e) {
            msg(client, "§c[ServerFinder] Censys error: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private JsonObject buildCensysQuery(int max) {
        JsonObject q = new JsonObject();
        q.addProperty("query",    "services.port=25565");
        q.addProperty("per_page", Math.min(max * 2, 100));
        JsonArray fields = new JsonArray();
        fields.add("ip"); fields.add("services.port");
        fields.add("services.service_name"); fields.add("services.banner");
        q.add("fields", fields);
        return q;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void msg(MinecraftClient client, String s) {
        if (client.player != null) client.player.sendMessage(Text.literal(s), false);
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
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
