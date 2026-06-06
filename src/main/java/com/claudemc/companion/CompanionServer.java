package com.claudemc.companion;

import com.claudemc.ClaudeMCMod;
import com.claudemc.account.AltManager;
import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.server.VulnDb;
import com.google.gson.*;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Embedded HTTP server that serves the ClaudeMC companion web app at http://localhost:8080.
 *
 * Routes:
 *   GET  /                      — companion.html
 *   POST /api/scan              — MCScans discovery + VulnDb cross-ref
 *   POST /api/shodan            — Shodan search + VulnDb cross-ref
 *   GET  /api/lookup            — mcsrvstat.us + mcstatus.io enrichment
 *   GET  /api/vulndb            — full VulnDb as JSON
 *   POST /api/chat              — AI free-form chat
 *   POST /api/analyze           — AI exploit analysis for a specific server
 *   GET  /api/settings          — read AIConfig + shodanApiKey
 *   POST /api/settings          — update AIConfig
 *   GET  /api/alts              — list alts
 *   POST /api/alts              — add alt
 *   POST /api/alts/switch       — switch to alt
 *   POST /api/alts/restore      — restore original session
 *   DELETE /api/alts            — remove alt by index
 */
public final class CompanionServer {

    public static final CompanionServer INSTANCE = new CompanionServer();

    private static final int    PORT = 8080;
    private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String UA   = "ClaudeMC-Companion/1.19";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    // In-memory chat history — synchronizedList because handler virtual threads access it concurrently
    private final List<Map<String, String>> chatHistory =
        Collections.synchronizedList(new ArrayList<>());

    private volatile HttpServer server;
    private volatile boolean    started    = false;
    private volatile int        boundPort  = PORT;

    private CompanionServer() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public void start() {
        if (started) return;
        Thread.ofVirtual().start(() -> {
            int port = PORT;
            for (int attempt = 0; attempt < 5; attempt++, port++) {
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                    server.createContext("/",                   this::serveStatic);
                    server.createContext("/api/scan",           this::handleScan);
                    server.createContext("/api/shodan",         this::handleShodan);
                    server.createContext("/api/censys",         this::handleCensys);
                    server.createContext("/api/fofa",           this::handleFofa);
                    server.createContext("/api/lookup",         this::handleLookup);
                    server.createContext("/api/vulndb",         this::handleVulnDb);
                    server.createContext("/api/chat",           this::handleChat);
                    server.createContext("/api/analyze",        this::handleAnalyze);
                    server.createContext("/api/settings",       this::handleSettings);
                    server.createContext("/api/alts",           this::handleAlts);
                    server.createContext("/api/alts/switch",    this::handleAltSwitch);
                    server.createContext("/api/alts/restore",   this::handleAltRestore);
                    server.start();
                    boundPort = port;
                    started   = true;
                    ClaudeMCMod.LOGGER.info("[Companion] Running at http://localhost:{}", port);
                    return;
                } catch (Exception e) {
                    ClaudeMCMod.LOGGER.warn("[Companion] Port {} in use, trying next.", port);
                }
            }
            ClaudeMCMod.LOGGER.warn("[Companion] Could not bind to any port in range {}-{}", PORT, port - 1);
        });
    }

    /** Returns the companion URL. */
    public String getUrl() {
        return started ? "http://localhost:" + boundPort : "";
    }

    /** Opens the companion page in the default browser with a multi-tier fallback chain. */
    public void open() {
        if (!started) start();
        // Brief spin-wait to let the async start() bind before we try to open (≤ 200 ms)
        long deadline = System.currentTimeMillis() + 200;
        while (!started && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
        final String url = "http://localhost:" + boundPort;
        Thread.ofVirtual().start(() -> {
            // 1. Try Desktop.browse
            try {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(URI.create(url));
                    return;
                }
            } catch (Exception ignored) {}

            // 2. OS-specific ProcessBuilder fallback
            String os = System.getProperty("os.name", "").toLowerCase();
            String[] cmd = null;
            if (os.contains("win"))  cmd = new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
            else if (os.contains("mac")) cmd = new String[]{"open", url};
            else                         cmd = new String[]{"xdg-open", url};
            try { new ProcessBuilder(cmd).start(); return; } catch (Exception ignored) {}

            // 3. Try common Linux browsers
            for (String browser : new String[]{"sensible-browser","firefox","chromium-browser","google-chrome"}) {
                try { new ProcessBuilder(browser, url).start(); return; } catch (Exception ignored) {}
            }

            // 4. Last resort: copy to clipboard
            try {
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(url);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                ClaudeMCMod.LOGGER.info("[Companion] URL copied to clipboard: {}", url);
            } catch (Exception e) {
                ClaudeMCMod.LOGGER.warn("[Companion] Cannot open browser: {}", e.getMessage());
            }
        });
    }

    // ── Static file ───────────────────────────────────────────────────────

    private void serveStatic(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        try (InputStream is = getClass().getResourceAsStream("/assets/claudemc/companion.html")) {
            if (is == null) { sendText(ex, 404, "Companion page not found"); return; }
            byte[] bytes = is.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        }
    }

    // ── POST /api/scan ────────────────────────────────────────────────────

    private void handleScan(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body = parseBody(ex);
            String software    = str(body, "software", "");
            String authMode    = str(body, "authMode", "");
            int    maxResults  = body.has("maxResults") ? body.get("maxResults").getAsInt() : 50;

            StringBuilder url = new StringBuilder(
                "https://api.mcscans.fi/public/v1/servers?live=true&sort=player&limit=" + maxResults);
            if (!software.isBlank() && !software.equals("all"))
                url.append("&software=").append(enc(software));
            if (authMode.equalsIgnoreCase("offline"))
                url.append("&authMode=Offline");

            String raw = httpGet(url.toString(), null);
            JsonArray servers = raw != null ? extractServers(raw) : new JsonArray();

            JsonArray results = new JsonArray();
            for (JsonElement el : servers) {
                JsonObject s    = el.getAsJsonObject();
                String     ip   = str(s, "hostname", "");
                int        port = s.has("port") ? s.get("port").getAsInt() : 25565;
                String     sw   = str(s, "software", "unknown");
                String     ver  = str(s, "version", "?");
                int        online = 0;
                if (s.has("playerStats") && s.get("playerStats").isJsonObject())
                    online = s.getAsJsonObject("playerStats").has("onlinePlayers")
                        ? s.getAsJsonObject("playerStats").get("onlinePlayers").getAsInt() : 0;
                int authM = s.has("authMode") ? s.get("authMode").getAsInt() : 1;

                JsonArray vulns = matchVulns(sw, ver, List.of());
                JsonObject row = new JsonObject();
                row.addProperty("ip",      ip);
                row.addProperty("port",    port);
                row.addProperty("software", sw);
                row.addProperty("version", ver);
                row.addProperty("players", online);
                row.addProperty("authMode", authM == 0 ? "Offline" : authM == 2 ? "Whitelist" : "Online");
                row.add("vulnerabilities", vulns);
                results.add(row);
            }

            sendJson(ex, 200, results);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/shodan ──────────────────────────────────────────────────

    private void handleShodan(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body  = parseBody(ex);
            String     query = str(body, "query", "port:25565 game:Minecraft");
            int        page  = body.has("page") ? body.get("page").getAsInt() : 1;
            String     key   = AIConfig.INSTANCE.shodanApiKey;

            if (key.isBlank()) {
                sendText(ex, 400, "Shodan API key not configured. Add it in Settings.");
                return;
            }

            String url = "https://api.shodan.io/shodan/host/search?key=" + enc(key)
                + "&query=" + enc(query) + "&page=" + page;
            String raw = httpGet(url, null);
            if (raw == null) { sendText(ex, 502, "Shodan request failed"); return; }

            JsonObject shodanResp = GSON.fromJson(raw, JsonObject.class);
            JsonArray  matches    = shodanResp.has("matches")
                ? shodanResp.getAsJsonArray("matches") : new JsonArray();

            JsonArray results = new JsonArray();
            for (JsonElement el : matches) {
                JsonObject m = el.getAsJsonObject();
                String ip   = str(m, "ip_str", "");
                int    port = m.has("port") ? m.get("port").getAsInt() : 25565;
                // Shodan Minecraft data is in "minecraft" sub-object when available
                String ver  = "";
                String sw   = "";
                if (m.has("minecraft") && m.get("minecraft").isJsonObject()) {
                    JsonObject mc = m.getAsJsonObject("minecraft");
                    ver = str(mc, "version", "");
                    sw  = str(mc, "server_type", "");
                }
                if (ver.isBlank()) ver = str(m, "version", "?");

                JsonArray vulns = matchVulns(sw, ver, List.of());
                JsonObject row  = new JsonObject();
                row.addProperty("ip",          ip);
                row.addProperty("port",        port);
                row.addProperty("software",    sw.isBlank() ? "unknown" : sw);
                row.addProperty("version",     ver);
                row.addProperty("org",         str(m, "org", ""));
                row.addProperty("country",     str(m, "country_name", ""));
                row.addProperty("source",      "shodan");
                row.add("vulnerabilities",     vulns);
                results.add(row);
            }

            JsonObject out = new JsonObject();
            out.add("results", results);
            out.addProperty("total", shodanResp.has("total") ? shodanResp.get("total").getAsInt() : 0);
            sendJson(ex, 200, out);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/censys ──────────────────────────────────────────────────

    private void handleCensys(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body  = parseBody(ex);
            String     query = str(body, "query", "services.port=25565");
            int        perPage = body.has("perPage") ? body.get("perPage").getAsInt() : 50;
            String     pat   = AIConfig.INSTANCE.censysApiKey;

            if (pat.isBlank()) {
                sendText(ex, 400, "Censys API key not configured. Add censysApiKey in Settings.");
                return;
            }

            JsonObject reqBody = new JsonObject();
            reqBody.addProperty("query",    query);
            reqBody.addProperty("per_page", Math.min(perPage, 100));
            JsonArray fields = new JsonArray();
            fields.add("ip"); fields.add("services.port");
            fields.add("services.service_name"); fields.add("services.banner");
            fields.add("location.country");
            reqBody.add("fields", fields);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.platform.censys.io/v3/global/asset/search"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept",        "application/json")
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + pat)
                .header("User-Agent",    UA)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(reqBody)))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                sendText(ex, 401, "Censys auth failed. Check your Personal Access Token.");
                return;
            }
            if (resp.statusCode() != 200) {
                sendText(ex, 502, "Censys returned HTTP " + resp.statusCode());
                return;
            }

            JsonObject censysResp = GSON.fromJson(resp.body(), JsonObject.class);
            JsonArray  hits  = censysResp.has("result")
                && censysResp.getAsJsonObject("result").has("hits")
                ? censysResp.getAsJsonObject("result").getAsJsonArray("hits") : new JsonArray();

            JsonArray results = new JsonArray();
            for (JsonElement el : hits) {
                if (!el.isJsonObject()) continue;
                JsonObject h   = el.getAsJsonObject();
                String     ip  = str(h, "ip", "");
                if (ip.isBlank()) continue;
                String sw = "", banner = "", country = "";
                int    port = 25565;
                if (h.has("services") && h.get("services").isJsonArray()) {
                    for (JsonElement svc : h.getAsJsonArray("services")) {
                        if (!svc.isJsonObject()) continue;
                        JsonObject sv = svc.getAsJsonObject();
                        int p = sv.has("port") ? sv.get("port").getAsInt() : 0;
                        if (p == 25565 || p == 0) {
                            port    = p == 0 ? 25565 : p;
                            sw      = str(sv, "service_name", "");
                            banner  = str(sv, "banner", "");
                            break;
                        }
                    }
                }
                if (h.has("location") && h.get("location").isJsonObject())
                    country = str(h.getAsJsonObject("location"), "country", "");

                JsonArray vulns = matchVulns(sw, "", List.of());
                JsonObject row  = new JsonObject();
                row.addProperty("ip",      ip);
                row.addProperty("port",    port);
                row.addProperty("software", sw.isBlank() ? "unknown" : sw);
                row.addProperty("banner",  banner);
                row.addProperty("country", country);
                row.addProperty("source",  "censys");
                row.add("vulnerabilities", vulns);
                results.add(row);
            }

            JsonObject out = new JsonObject();
            out.add("results", results);
            out.addProperty("total", results.size());
            sendJson(ex, 200, out);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/fofa ────────────────────────────────────────────────────

    private void handleFofa(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body  = parseBody(ex);
            String     query = str(body, "query", "port=\"25565\" && protocol=\"minecraft\"");
            int        size  = body.has("size") ? body.get("size").getAsInt() : 50;
            String     key   = AIConfig.INSTANCE.fofaApiKey;

            if (key.isBlank()) {
                sendText(ex, 400, "FOFA API key not configured. Add fofaApiKey in Settings.");
                return;
            }

            String qb64   = Base64.getEncoder().encodeToString(query.getBytes(StandardCharsets.UTF_8));
            String fields = "ip,port,protocol,country,banner,host";
            String url    = "https://fofa.info/api/v1/search/all"
                + "?key="     + enc(key)
                + "&qbase64=" + qb64
                + "&fields="  + enc(fields)
                + "&size="    + Math.min(size, 100);

            String raw = httpGet(url, UA);
            if (raw == null) { sendText(ex, 502, "FOFA request failed"); return; }

            JsonObject fofaResp = GSON.fromJson(raw, JsonObject.class);
            if (fofaResp.has("errmsg")) {
                sendText(ex, 400, "FOFA error: " + fofaResp.get("errmsg").getAsString());
                return;
            }

            JsonArray fofaResults = fofaResp.has("results")
                ? fofaResp.getAsJsonArray("results") : new JsonArray();

            // Each result: [ip, port, protocol, country, banner, host]
            JsonArray results = new JsonArray();
            for (JsonElement el : fofaResults) {
                if (!el.isJsonArray()) continue;
                JsonArray row = el.getAsJsonArray();
                String ip      = safeGet(row, 0);
                String port    = safeGet(row, 1);
                String proto   = safeGet(row, 2);
                String country = safeGet(row, 3);
                String banner  = safeGet(row, 4);
                String host    = safeGet(row, 5);
                if (ip.isBlank()) continue;

                JsonArray vulns = matchVulns(proto, "", List.of());
                JsonObject r    = new JsonObject();
                r.addProperty("ip",       ip);
                r.addProperty("port",     port.isBlank() ? "25565" : port);
                r.addProperty("software", proto.isBlank() ? "unknown" : proto);
                r.addProperty("banner",   banner);
                r.addProperty("country",  country);
                r.addProperty("host",     host);
                r.addProperty("source",   "fofa");
                r.add("vulnerabilities",  vulns);
                results.add(r);
            }

            JsonObject out = new JsonObject();
            out.add("results", results);
            out.addProperty("total", fofaResp.has("size") ? fofaResp.get("size").getAsInt() : results.size());
            sendJson(ex, 200, out);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    private static String safeGet(JsonArray a, int i) {
        return (i < a.size() && !a.get(i).isJsonNull()) ? a.get(i).getAsString() : "";
    }

    // ── GET /api/lookup?address=IP:PORT ───────────────────────────────────

    private void handleLookup(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            String address = queryParam(ex.getRequestURI().getQuery(), "address");
            if (address == null || address.isBlank()) { sendText(ex, 400, "Missing address"); return; }

            // Fire both lookups concurrently
            CompletableFuture<String> mcsrvFut = CompletableFuture.supplyAsync(() ->
                httpGet("https://api.mcsrvstat.us/3/" + enc(address), UA));
            CompletableFuture<String> mcstatFut = CompletableFuture.supplyAsync(() ->
                httpGet("https://api.mcstatus.io/v2/status/java/" + enc(address), null));

            String mcsrvRaw  = mcsrvFut.get(12, TimeUnit.SECONDS);
            String mcstatRaw = mcstatFut.get(12, TimeUnit.SECONDS);

            JsonObject merged = new JsonObject();

            // Parse mcsrvstat (richer plugin/mod data)
            if (mcsrvRaw != null) {
                try {
                    JsonObject ms = GSON.fromJson(mcsrvRaw, JsonObject.class);
                    merged.addProperty("online",   ms.has("online") && ms.get("online").getAsBoolean());
                    merged.addProperty("ip",       str(ms, "ip", address));
                    merged.addProperty("port",     ms.has("port") ? ms.get("port").getAsInt() : 25565);
                    merged.addProperty("software", str(ms, "software", ""));
                    merged.addProperty("version",  str(ms, "version", ""));
                    if (ms.has("motd") && ms.get("motd").isJsonObject())
                        merged.addProperty("motd", ms.getAsJsonObject("motd").has("clean")
                            ? ms.getAsJsonObject("motd").getAsJsonArray("clean").toString() : "");
                    if (ms.has("players") && ms.get("players").isJsonObject()) {
                        JsonObject pl = ms.getAsJsonObject("players");
                        merged.addProperty("playersOnline", pl.has("online") ? pl.get("online").getAsInt() : 0);
                        merged.addProperty("playersMax",    pl.has("max")    ? pl.get("max").getAsInt()    : 0);
                        if (pl.has("list")) merged.add("playerList", pl.getAsJsonArray("list"));
                    }
                    if (ms.has("plugins")) merged.add("plugins", ms.getAsJsonArray("plugins"));
                    if (ms.has("mods"))    merged.add("mods",    ms.getAsJsonArray("mods"));
                } catch (Exception ignored) {}
            }

            // Supplement with mcstatus.io data for any missing fields
            if (mcstatRaw != null) {
                try {
                    JsonObject mc = GSON.fromJson(mcstatRaw, JsonObject.class);
                    if (!merged.has("online"))
                        merged.addProperty("online", mc.has("online") && mc.get("online").getAsBoolean());
                    if (!merged.has("software") || str(merged, "software", "").isBlank())
                        merged.addProperty("software", str(mc, "software", ""));
                    if (!merged.has("version") || str(merged, "version", "").isBlank())
                        merged.addProperty("version", mc.has("version") && mc.get("version").isJsonObject()
                            ? str(mc.getAsJsonObject("version"), "name_raw", "") : "");
                } catch (Exception ignored) {}
            }

            // VulnDb cross-reference
            List<String> pluginNames = new ArrayList<>();
            if (merged.has("plugins")) {
                for (JsonElement p : merged.getAsJsonArray("plugins"))
                    if (p.isJsonObject()) pluginNames.add(str(p.getAsJsonObject(), "name", ""));
            }
            merged.add("vulnerabilities", matchVulns(
                str(merged, "software", ""), str(merged, "version", ""), pluginNames));

            merged.addProperty("source_mcsrvstat", mcsrvRaw != null ? "ok" : "failed");
            merged.addProperty("source_mcstatus",  mcstatRaw != null ? "ok" : "failed");
            sendJson(ex, 200, merged);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── GET /api/vulndb ───────────────────────────────────────────────────

    private void handleVulnDb(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        JsonArray arr = new JsonArray();
        for (VulnDb.VulnEntry e : VulnDb.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("plugin",          e.pluginName());
            o.addProperty("severity",        e.severity().name());
            o.addProperty("affectedVersions",e.affectedVersions());
            o.addProperty("description",     e.description());
            o.addProperty("patchedIn",       e.patchedIn());
            arr.add(o);
        }
        sendJson(ex, 200, arr);
    }

    // ── POST /api/chat ────────────────────────────────────────────────────

    private void handleChat(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body = parseBody(ex);
            String message  = str(body, "message", "").trim();
            if (message.isBlank()) { sendText(ex, 400, "Empty message"); return; }

            if (!AIConfig.INSTANCE.isConfigured()) {
                sendText(ex, 400, "No AI key configured. Add one in Settings.");
                return;
            }

            // Build prompt with recent history context (last 6 turns)
            StringBuilder prompt = new StringBuilder();
            int start = Math.max(0, chatHistory.size() - 6);
            for (int i = start; i < chatHistory.size(); i++) {
                Map<String, String> turn = chatHistory.get(i);
                prompt.append(turn.get("role")).append(": ").append(turn.get("content")).append("\n");
            }
            prompt.append("User: ").append(message);

            String chatSys =
                "You are ClaudeMC Companion, an AI assistant for a Minecraft hacking mod. " +
                "Answer questions about Minecraft servers, exploits, plugins, and vulnerabilities. " +
                "You may use markdown formatting in your response.";

            CountDownLatch latch = new CountDownLatch(1);
            String[]       reply = {""};

            AIClient.INSTANCE.ask(chatSys, prompt.toString(),
                resp -> { reply[0] = resp; latch.countDown(); },
                err  -> { reply[0] = "Error: " + err; latch.countDown(); });

            if (!latch.await(30, TimeUnit.SECONDS)) {
                // Don't append an empty assistant turn to history on timeout
                sendText(ex, 504, "AI request timed out after 30s. Check your API key/model and connection.");
                return;
            }

            chatHistory.add(Map.of("role", "User",      "content", message));
            chatHistory.add(Map.of("role", "Assistant", "content", reply[0]));

            JsonObject out = new JsonObject();
            out.addProperty("reply", reply[0]);
            sendJson(ex, 200, out);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── POST /api/analyze ─────────────────────────────────────────────────

    private void handleAnalyze(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body    = parseBody(ex);
            String     ip      = str(body, "ip",       "unknown");
            String     software = str(body, "software", "unknown");
            String     version = str(body, "version",  "unknown");
            JsonArray  pArr    = body.has("plugins") ? body.getAsJsonArray("plugins") : new JsonArray();
            List<String> plugins = new ArrayList<>();
            for (JsonElement p : pArr) plugins.add(p.getAsString());

            // Collect matching VulnDb entries
            JsonArray vulns = matchVulns(software, version, plugins);

            if (!AIConfig.INSTANCE.isConfigured()) {
                // Return VulnDb-only analysis without AI
                JsonObject out = new JsonObject();
                out.addProperty("server", ip);
                out.add("knownVulnerabilities", vulns);
                out.addProperty("aiAnalysis", "No AI key configured — showing VulnDb matches only.");
                sendJson(ex, 200, out);
                return;
            }

            String vulnSummary = new StringBuilder().append("Known vulnerabilities:\n")
                .append(vulns.toString()).toString();

            String prompt = "A Minecraft server at " + ip + " runs " + software + " " + version +
                " with plugins: " + String.join(", ", plugins) + ".\n" + vulnSummary +
                "\n\nProvide a concise numbered list of exploits that likely work on this server. " +
                "For each: name, what it achieves, exact commands/steps. " +
                "Focus on the most impactful exploits first. Use markdown.";

            String analyzeSys =
                "You are a Minecraft server penetration testing assistant. " +
                "Provide specific, actionable exploit steps. Use markdown formatting.";

            CountDownLatch latch    = new CountDownLatch(1);
            String[]       analysis = {""};

            AIClient.INSTANCE.ask(analyzeSys, prompt,
                resp -> { analysis[0] = resp; latch.countDown(); },
                err  -> { analysis[0] = "AI error: " + err; latch.countDown(); });

            if (!latch.await(45, TimeUnit.SECONDS)) {
                analysis[0] = "AI analysis timed out after 45s — showing VulnDb matches only.";
            }

            JsonObject out = new JsonObject();
            out.addProperty("server",     ip);
            out.addProperty("software",   software);
            out.addProperty("version",    version);
            out.add("knownVulnerabilities", vulns);
            out.addProperty("aiAnalysis", analysis[0]);
            sendJson(ex, 200, out);
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── /api/settings ─────────────────────────────────────────────────────

    private void handleSettings(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("GET")) {
            JsonObject out = new JsonObject();
            out.addProperty("provider",     AIConfig.INSTANCE.provider);
            out.addProperty("anthropicKey", AIConfig.INSTANCE.anthropicKey);
            out.addProperty("openaiKey",    AIConfig.INSTANCE.openaiKey);
            out.addProperty("geminiKey",    AIConfig.INSTANCE.geminiKey);
            out.addProperty("shodanApiKey",  AIConfig.INSTANCE.shodanApiKey);
            out.addProperty("censysApiKey",  AIConfig.INSTANCE.censysApiKey);
            out.addProperty("fofaApiKey",    AIConfig.INSTANCE.fofaApiKey);
            out.addProperty("model",        AIConfig.INSTANCE.model);
            out.addProperty("maxTokens",    AIConfig.INSTANCE.maxTokens);
            out.addProperty("systemPrompt", AIConfig.INSTANCE.systemPrompt);
            sendJson(ex, 200, out);
        } else if (ex.getRequestMethod().equals("POST")) {
            try {
                JsonObject body = parseBody(ex);
                if (body.has("provider"))     AIConfig.INSTANCE.provider     = str(body, "provider",     AIConfig.INSTANCE.provider);
                if (body.has("anthropicKey")) AIConfig.INSTANCE.anthropicKey = str(body, "anthropicKey", AIConfig.INSTANCE.anthropicKey);
                if (body.has("openaiKey"))    AIConfig.INSTANCE.openaiKey    = str(body, "openaiKey",    AIConfig.INSTANCE.openaiKey);
                if (body.has("geminiKey"))    AIConfig.INSTANCE.geminiKey    = str(body, "geminiKey",    AIConfig.INSTANCE.geminiKey);
                if (body.has("shodanApiKey")) AIConfig.INSTANCE.shodanApiKey = str(body, "shodanApiKey", AIConfig.INSTANCE.shodanApiKey);
                if (body.has("censysApiKey")) AIConfig.INSTANCE.censysApiKey = str(body, "censysApiKey", AIConfig.INSTANCE.censysApiKey);
                if (body.has("fofaApiKey"))   AIConfig.INSTANCE.fofaApiKey   = str(body, "fofaApiKey",   AIConfig.INSTANCE.fofaApiKey);
                if (body.has("model"))        AIConfig.INSTANCE.model        = str(body, "model",        AIConfig.INSTANCE.model);
                if (body.has("maxTokens"))    AIConfig.INSTANCE.maxTokens    = body.get("maxTokens").getAsInt();
                if (body.has("systemPrompt")) AIConfig.INSTANCE.systemPrompt = str(body, "systemPrompt", AIConfig.INSTANCE.systemPrompt);
                AIConfig.save();
                sendJson(ex, 200, new JsonObject());
            } catch (Exception e) {
                sendText(ex, 500, e.getMessage());
            }
        } else {
            ex.sendResponseHeaders(405, -1);
        }
    }

    // ── /api/alts ─────────────────────────────────────────────────────────

    private void handleAlts(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        try {
            if (method.equals("GET")) {
                JsonObject out = new JsonObject();
                out.addProperty("activeUsername", AltManager.INSTANCE.getActiveUsername());
                out.addProperty("isUsingAlt",     AltManager.INSTANCE.isUsingAlt());
                JsonArray arr = new JsonArray();
                int idx = 0;
                for (AltManager.AltEntry a : AltManager.INSTANCE.getAlts()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("index", idx++);
                    o.addProperty("name",  a.name);
                    o.addProperty("type",  a.type.name());
                    arr.add(o);
                }
                out.add("alts", arr);
                sendJson(ex, 200, out);

            } else if (method.equals("POST")) {
                JsonObject body = parseBody(ex);
                String type = str(body, "type", "offline");
                if (type.equalsIgnoreCase("session")) {
                    AltManager.INSTANCE.addSession(
                        str(body, "name",        ""),
                        str(body, "uuid",        ""),
                        str(body, "accessToken", ""));
                } else {
                    AltManager.INSTANCE.addOffline(str(body, "name", ""));
                }
                sendJson(ex, 200, new JsonObject());

            } else if (method.equals("DELETE")) {
                String idxStr = queryParam(ex.getRequestURI().getQuery(), "index");
                if (idxStr == null) { sendText(ex, 400, "Missing index"); return; }
                AltManager.INSTANCE.remove(Integer.parseInt(idxStr));
                sendJson(ex, 200, new JsonObject());

            } else {
                ex.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    private void handleAltSwitch(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body = parseBody(ex);
            if (!body.has("index")) { sendText(ex, 400, "Missing index"); return; }
            AltManager.INSTANCE.switchTo(body.get("index").getAsInt());
            sendJson(ex, 200, new JsonObject());
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    private void handleAltRestore(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            AltManager.INSTANCE.restore();
            sendJson(ex, 200, new JsonObject());
        } catch (Exception e) {
            sendText(ex, 500, e.getMessage());
        }
    }

    // ── VulnDb cross-reference ────────────────────────────────────────────

    private JsonArray matchVulns(String software, String version, List<String> plugins) {
        JsonArray arr = new JsonArray();
        String swLower = software.toLowerCase();
        List<String> pluginLower = plugins.stream()
            .map(String::toLowerCase).collect(Collectors.toList());

        for (VulnDb.VulnEntry e : VulnDb.all()) {
            String pn = e.pluginName().toLowerCase();
            boolean match = swLower.contains(pn) || pn.contains(swLower)
                || pluginLower.stream().anyMatch(p -> p.contains(pn) || pn.contains(p));
            if (!match) continue;
            JsonObject o = new JsonObject();
            o.addProperty("plugin",      e.pluginName());
            o.addProperty("severity",    e.severity().name());
            o.addProperty("description", e.description());
            o.addProperty("patchedIn",   e.patchedIn());
            arr.add(o);
        }
        return arr;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private String httpGet(String url, String userAgent) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent != null ? userAgent : UA);
            HttpResponse<String> resp = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private JsonArray extractServers(String raw) {
        try {
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            return root.has("servers") ? root.getAsJsonArray("servers") : new JsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }

    private JsonObject parseBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return s.isBlank() ? new JsonObject() : GSON.fromJson(s, JsonObject.class);
        }
    }

    private void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        byte[] bytes = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        // No wildcard CORS: the companion page is served same-origin from this server.
        // A "*" header would let any website the user visits read /api/settings (API keys),
        // drive /api/alts/switch, etc. cross-origin against localhost. Keep responses same-origin only.
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void sendText(HttpExchange ex, int code, String msg) throws IOException {
        byte[] bytes = (msg == null ? "" : msg).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            try {
                if (URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).equals(key))
                    return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
