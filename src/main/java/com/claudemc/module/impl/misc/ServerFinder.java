package com.claudemc.module.impl.misc;

import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.ai.WebSearch;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.server.VulnDb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/**
 * ServerFinder — queries mcscans.fi for live Minecraft servers and filters them by:
 *   - Vulnerability: matches server software/plugins against VulnDb
 *   - P2W / child-gambling: uses web search + AI to identify known predatory servers
 *
 * Results are printed to local chat. No data is sent anywhere except mcscans.fi.
 */
public class ServerFinder extends Module {

    public static ServerFinder INSTANCE;

    private static final String API_BASE = "https://api.mcscans.fi/public/v1/servers";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── Known P2W / child-gambling servers (community-sourced) ────────────
    // These servers are publicly identified on p2w.report and anti-P2W communities
    // as selling gameplay advantages and/or gambling mechanics targeting minors.
    private static final Set<String> KNOWN_P2W = Set.of(
        "cosmicpvp", "mineclex", "manacube", "purpleprison", "gotpvp",
        "opfactions", "ultraprison", "mineland", "hypixel" /* skyblock cosmetics */,
        "cubovation", "veltpvp", "mineplex", "cubecraft",
        "playlegend", "justmysocks", "jackpotmc", "lemoncloud",
        "skyblock.net", "moxmc", "minecraftprison",
        "foxcraft", "minetime", "orionmc", "galaxyminecraft",
        "extradragons", "opcraft", "skyblock.games", "zeqa"
    );

    public ServerFinder() {
        super("ServerFinder",
              "Scans mcscans.fi for vulnerable / P2W servers. Results in local chat.",
              Category.MISC);
        addMode("Mode", "Both", "Both", "Vulnerable", "P2W");
        addBool("UseAI",        true);
        addNumber("MaxResults", 20, 5, 100, 5, true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {}   // trigger-only module

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

    @Override
    public void onDisable() { running.set(false); }

    // ── Scan pipeline ─────────────────────────────────────────────────────

    private void scan(MinecraftClient client) {
        try {
            // 1. Fetch server list from mcscans.fi
            String json = fetch(API_BASE);
            if (json == null) {
                msg(client, "§c[ServerFinder] §7Failed to reach mcscans.fi"); return;
            }

            List<ServerEntry> servers = parseServers(json);
            int    maxResults = parseInt(getSetting("MaxResults"), 20);
            String mode       = getSetting("Mode");

            // 2. Vulnerability scan
            if (mode.equals("Both") || mode.equals("Vulnerable")) {
                List<ServerEntry> vulnServers = findVulnerable(servers, maxResults);
                if (vulnServers.isEmpty()) {
                    msg(client, "§6[ServerFinder] §7No clearly vulnerable servers found in this page.");
                } else {
                    msg(client, "§c§l[ServerFinder] Vulnerable servers (" + vulnServers.size() + "):");
                    for (ServerEntry s : vulnServers) {
                        msg(client, "§c  " + s.ip + ":" + s.port
                            + " §7| " + s.version + " | §e" + s.vulnSummary);
                    }
                }
            }

            // 3. P2W scan — match name/motd against known list + AI
            if (mode.equals("Both") || mode.equals("P2W")) {
                List<ServerEntry> p2wServers = findP2W(servers, maxResults);
                msg(client, "§d§l[ServerFinder] Likely P2W / child-gambling servers (" + p2wServers.size() + "):");
                for (ServerEntry s : p2wServers)
                    msg(client, "§d  " + s.ip + ":" + s.port + " §7| " + s.version + " | §e" + s.motd);

                // Also do AI + web search for additional known P2W servers
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

    // ── Vulnerability matching ─────────────────────────────────────────────

    private List<ServerEntry> findVulnerable(List<ServerEntry> servers, int max) {
        List<ServerEntry> out = new ArrayList<>();
        for (ServerEntry s : servers) {
            if (out.size() >= max) break;
            String combined = (s.version + " " + s.motd).toLowerCase();
            for (VulnDb.VulnEntry v : VulnDb.all()) {
                if (v.severity() == VulnDb.Severity.PATCHED) continue;
                if (combined.contains(v.pluginName().toLowerCase())) {
                    s.vulnSummary = v.severity() + ": " + v.pluginName() + " — " + v.description().substring(0, Math.min(60, v.description().length()));
                    out.add(s);
                    break;
                }
            }
            // Also flag unpatched Spigot/CraftBukkit/BungeeCord by version string
            if (s.vulnSummary.isEmpty() && isLikelyVulnerableSoftware(combined)) {
                s.vulnSummary = "Likely unpatched: " + extractSoftware(s.version);
                out.add(s);
            }
        }
        return out;
    }

    private boolean isLikelyVulnerableSoftware(String v) {
        return v.contains("craftbukkit") || v.contains("spigot") || v.contains("bungeecord")
            || (v.contains("waterfall")) || v.contains("mohist") || v.contains("magma");
    }

    private String extractSoftware(String version) {
        for (String sw : new String[]{"Paper","Spigot","CraftBukkit","Purpur","BungeeCord","Waterfall","Fabric","Forge","Mohist","Velocity"})
            if (version.toLowerCase().contains(sw.toLowerCase())) return sw;
        return version;
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
            msg(client, "§d[ServerFinder] §7Web results — known P2W ecosystems:");
            for (String s : snippets) msg(client, "§7  • " + s.substring(0, Math.min(120, s.length())));
        }
    }

    private void aiP2WList(MinecraftClient client) {
        // Run web search first, pass snippets to AI
        List<String> snippets = WebSearch.search(
            "minecraft pay to win child gambling servers list crate keys 2024 2025 p2w report", 8);

        String webCtx = snippets.isEmpty() ? "" :
            "\nWeb results:\n" + String.join("\n", snippets.subList(0, Math.min(4, snippets.size())));

        String saved = AIConfig.INSTANCE.systemPrompt;
        AIConfig.INSTANCE.systemPrompt =
            "You list Minecraft servers. Be concise. Output: numbered list only, format: ServerName | IP (if known) | Why P2W";
        AIClient.INSTANCE.ask(
            "List up to 15 well-known Minecraft servers that are pay-to-win or have child gambling mechanics "
            + "(crate keys, OP spawners for sale, /fly for pay, selling in-game currency). "
            + "Focus on servers still active in 2024-2025." + webCtx,
            resp -> {
                AIConfig.INSTANCE.systemPrompt = saved;
                msg(client, "§d§l[ServerFinder] AI P2W/gambling server list:");
                for (String line : resp.split("\n")) {
                    String t = line.trim();
                    if (!t.isBlank()) msg(client, "§d  " + t);
                }
            },
            err -> { AIConfig.INSTANCE.systemPrompt = saved;
                     webP2WList(client); } // fallback to web snippets
        );
    }

    // ── JSON parsing (Gson-free) ───────────────────────────────────────────

    private List<ServerEntry> parseServers(String json) {
        List<ServerEntry> out = new ArrayList<>();
        // Matches each server object block between braces
        Pattern block = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);
        Matcher m = block.matcher(json);
        while (m.find()) {
            String obj = m.group();
            String ip      = field(obj, "ip", "address", "host");
            String port    = field(obj, "port");
            String version = field(obj, "version", "version_name", "name");
            String motd    = field(obj, "motd", "description", "desc");
            if (ip != null && !ip.isBlank()) {
                ServerEntry e = new ServerEntry();
                e.ip = ip; e.port = port != null ? port : "25565";
                e.version = version != null ? version : "Unknown";
                e.motd    = motd    != null ? motd.replaceAll("§.", "") : "";
                out.add(e);
            }
        }
        return out;
    }

    /** Extracts a string value for the first matching key from a JSON object substring. */
    private String field(String obj, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"");
            Matcher m = p.matcher(obj);
            if (m.find()) return m.group(1);
            // Also try numeric
            Pattern n = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
            Matcher mn = n.matcher(obj);
            if (mn.find()) return mn.group(1);
        }
        return null;
    }

    // ── HTTP ──────────────────────────────────────────────────────────────

    private String fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 ClaudeMC/1.16")
                .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) { return null; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void msg(MinecraftClient client, String s) {
        if (client.player != null) client.player.sendMessage(Text.literal(s), false);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static class ServerEntry {
        String ip, port, version, motd, vulnSummary = "";
    }
}
