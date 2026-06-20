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

    // Web console port probe — {searchTerm, displayName, xssNote}
    private static final int[]      WEB_PORTS = {4200, 8080, 8443, 5000, 9090, 8090, 3000, 25580};
    private static final String[][] PANEL_SIGS = {
        {"pterodactyl",               "Pterodactyl Panel",     "Modern, well-maintained panel. Generally safe when up to date."},
        {"mcmyadmin",                 "McMyAdmin",             "Older versions had XSS in the console log viewer. Check the panel version."},
        {"crafty",                    "Crafty Controller",     "Versions before 4.1.0 had XSS in console output."},
        {"application management panel", "AMP (CubeCoders)",  "Generally safe if kept up to date."},
        {"multicraft",                "Multicraft",            "Older versions had XSS issues in the console log."},
        {"pufferpanel",               "PufferPanel",           "Modern panel. Generally safe when up to date."},
        {"mineos",                    "MineOS",                "Older versions may have XSS in the console output."},
    };

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    // In-memory chat history — synchronizedList because handler virtual threads access it concurrently
    private final List<Map<String, String>> chatHistory =
        Collections.synchronizedList(new ArrayList<>());

    private volatile HttpServer      server;
    private volatile ExecutorService executor;
    private volatile boolean         started    = false;
    private volatile int             boundPort  = PORT;

    // Per-session CSRF token — embedded in the companion page at serve time
    private final String csrfToken = generateCsrfToken();

    // Sliding-window rate limiter — shared across all proxied third-party API endpoints
    private static final int RATE_LIMIT_RPM = 30;
    private final java.util.concurrent.ConcurrentLinkedDeque<Long> apiRateWindow =
        new java.util.concurrent.ConcurrentLinkedDeque<>();

    private CompanionServer() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public void start() {
        if (started) return;
        Thread.ofVirtual().start(() -> {
            int port = PORT;
            for (int attempt = 0; attempt < 5; attempt++, port++) {
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                    executor = Executors.newVirtualThreadPerTaskExecutor();
                    server.setExecutor(executor);
                    server.createContext("/",                   secured(this::serveStatic));
                    server.createContext("/api/scan",           secured(this::handleScan));
                    server.createContext("/api/shodan",         secured(this::handleShodan));
                    server.createContext("/api/censys",         secured(this::handleCensys));
                    server.createContext("/api/fofa",           secured(this::handleFofa));
                    server.createContext("/api/lookup",         secured(this::handleLookup));
                    server.createContext("/api/vulndb",         secured(this::handleVulnDb));
                    server.createContext("/api/chat",           secured(this::handleChat));
                    server.createContext("/api/analyze",        secured(this::handleAnalyze));
                    server.createContext("/api/settings",       secured(this::handleSettings));
                    server.createContext("/api/alts",           secured(this::handleAlts));
                    server.createContext("/api/alts/switch",    secured(this::handleAltSwitch));
                    server.createContext("/api/alts/restore",   secured(this::handleAltRestore));
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

    /** Stops the embedded server and releases its executor (called on client shutdown). */
    public void stop() {
        if (!started) return;
        started = false;
        try { if (server != null) server.stop(0); } catch (Exception ignored) {}
        try { if (executor != null) executor.shutdownNow(); } catch (Exception ignored) {}
        server = null;
        executor = null;
        ClaudeMCMod.LOGGER.info("[Companion] Stopped.");
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

    // ── Request guard (anti-DNS-rebinding / anti-cross-origin) ─────────────

    /**
     * Wraps a handler so every request must originate from a genuine localhost client.
     *
     * Two checks defeat the browser-based attack surface:
     *   - Host header must be localhost / 127.0.0.1 / [::1]. A DNS-rebinding page reaches us
     *     with its own domain in Host (e.g. "evil.com:8080"), so this rejects it outright.
     *   - If an Origin header is present it must also be a localhost origin, blocking ordinary
     *     cross-origin fetches from any website the user happens to have open.
     *
     * The companion page is same-origin to this server, so legitimate requests always pass.
     */
    private HttpHandler secured(HttpHandler delegate) {
        return ex -> {
            if (!isLocalRequest(ex)) {
                ClaudeMCMod.LOGGER.warn("[Companion] Rejected request: Host='{}' Origin='{}'",
                    ex.getRequestHeaders().getFirst("Host"),
                    ex.getRequestHeaders().getFirst("Origin"));
                sendText(ex, 403, "Forbidden: requests must come from localhost.");
                return;
            }
            // Require X-ClaudeMC-Token on state-changing requests to block CSRF
            String reqMethod = ex.getRequestMethod();
            if (reqMethod.equals("POST") || reqMethod.equals("DELETE") || reqMethod.equals("PUT")) {
                String tok = ex.getRequestHeaders().getFirst("X-ClaudeMC-Token");
                if (!csrfToken.equals(tok)) {
                    sendText(ex, 403, "Forbidden");
                    return;
                }
            }
            delegate.handle(ex);
        };
    }

    private boolean isLocalRequest(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (!isLocalHostHeader(host)) return false;

        // Origin is sent on cross-origin requests (and CORS preflights); if present, pin it.
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank() && !"null".equalsIgnoreCase(origin)) {
            try {
                URI u = URI.create(origin);
                if (!isLoopbackName(u.getHost())) return false;
            } catch (Exception bad) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocalHostHeader(String host) {
        if (host == null || host.isBlank()) return false;
        // Strip port. IPv6 literals arrive as "[::1]:8080".
        String name;
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            name = close > 0 ? host.substring(1, close) : host;
        } else {
            int colon = host.lastIndexOf(':');
            name = colon > 0 ? host.substring(0, colon) : host;
        }
        return isLoopbackName(name);
    }

    private static boolean isLoopbackName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.equals("localhost") || n.equals("127.0.0.1") || n.equals("::1") || n.equals("0:0:0:0:0:0:0:1");
    }

    // ── Static file ───────────────────────────────────────────────────────

    private void serveStatic(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        try (InputStream is = getClass().getResourceAsStream("/assets/claudemc/companion.html")) {
            if (is == null) { sendText(ex, 404, "Companion page not found"); return; }
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // Inject per-session CSRF token so companion.html JS can read window.__csrf
            html = html.replace("</head>",
                "<script>window.__csrf='" + csrfToken + "';</script></head>");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
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
            int    maxResults  = gInt(body, "maxResults", 50);

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
                int        port = gInt(s, "port", 25565);
                String     sw   = str(s, "software", "unknown");
                String     ver  = str(s, "version", "?");
                int        online = 0;
                if (s.has("playerStats") && s.get("playerStats").isJsonObject())
                    online = gInt(s.getAsJsonObject("playerStats"), "onlinePlayers", 0);
                int authM = gInt(s, "authMode", 1);

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
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    // ── POST /api/shodan ──────────────────────────────────────────────────

    private void handleShodan(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        if (isRateLimited()) { sendText(ex, 429, "Too many requests — try again shortly."); return; }
        try {
            JsonObject body  = parseBody(ex);
            String     query = str(body, "query", "port:25565 game:Minecraft");
            int        page  = gInt(body, "page", 1);
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
                int    port = gInt(m, "port", 25565);
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
            out.addProperty("total", gInt(shodanResp, "total", 0));
            sendJson(ex, 200, out);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    // ── POST /api/censys ──────────────────────────────────────────────────

    private void handleCensys(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        if (isRateLimited()) { sendText(ex, 429, "Too many requests — try again shortly."); return; }
        try {
            JsonObject body  = parseBody(ex);
            String     query = str(body, "query", "services.port=25565");
            int        perPage = gInt(body, "perPage", 50);
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
                        int p = gInt(sv, "port", 0);
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
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    // ── POST /api/fofa ────────────────────────────────────────────────────

    private void handleFofa(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        if (isRateLimited()) { sendText(ex, 429, "Too many requests — try again shortly."); return; }
        try {
            JsonObject body  = parseBody(ex);
            String     query = str(body, "query", "port=\"25565\" && protocol=\"minecraft\"");
            int        size  = gInt(body, "size", 50);
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
            out.addProperty("total", gInt(fofaResp, "size", results.size()));
            sendJson(ex, 200, out);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
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

            // Extract bare hostname for web console probing (strip :port)
            String host = address.contains(":") ? address.substring(0, address.lastIndexOf(':')) : address;

            if (!isAllowedLookupTarget(host)) {
                sendText(ex, 400, "Private, loopback, or link-local addresses are not allowed.");
                return;
            }

            // Fire all three lookups concurrently
            CompletableFuture<String>     mcsrvFut  = CompletableFuture.supplyAsync(() ->
                httpGet("https://api.mcsrvstat.us/3/" + enc(address), UA));
            CompletableFuture<String>     mcstatFut = CompletableFuture.supplyAsync(() ->
                httpGet("https://api.mcstatus.io/v2/status/java/" + enc(address), null));
            CompletableFuture<JsonObject> webFut    = CompletableFuture.supplyAsync(() ->
                probeWebConsoles(host));

            String mcsrvRaw  = mcsrvFut.get(12, TimeUnit.SECONDS);
            String mcstatRaw = mcstatFut.get(12, TimeUnit.SECONDS);

            JsonObject merged = new JsonObject();

            // Parse mcsrvstat (richer plugin/mod data)
            if (mcsrvRaw != null) {
                try {
                    JsonObject ms = GSON.fromJson(mcsrvRaw, JsonObject.class);
                    merged.addProperty("online",   gBool(ms, "online", false));
                    merged.addProperty("ip",       str(ms, "ip", address));
                    merged.addProperty("port",     gInt(ms, "port", 25565));
                    merged.addProperty("software", str(ms, "software", ""));
                    merged.addProperty("version",  str(ms, "version", ""));
                    if (ms.has("motd") && ms.get("motd").isJsonObject())
                        merged.addProperty("motd", ms.getAsJsonObject("motd").has("clean")
                            ? ms.getAsJsonObject("motd").getAsJsonArray("clean").toString() : "");
                    if (ms.has("players") && ms.get("players").isJsonObject()) {
                        JsonObject pl = ms.getAsJsonObject("players");
                        merged.addProperty("playersOnline", gInt(pl, "online", 0));
                        merged.addProperty("playersMax",    gInt(pl, "max",    0));
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
                        merged.addProperty("online", gBool(mc, "online", false));
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

            // Web console probe result
            JsonObject wc;
            try { wc = webFut.get(8, TimeUnit.SECONDS); } catch (Exception e) { wc = null; }
            if (wc == null) { wc = new JsonObject(); wc.addProperty("detected", false); }
            merged.add("webConsole", wc);

            merged.addProperty("source_mcsrvstat", mcsrvRaw != null ? "ok" : "failed");
            merged.addProperty("source_mcstatus",  mcstatRaw != null ? "ok" : "failed");
            sendJson(ex, 200, merged);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    // ── Web console port probing ──────────────────────────────────────────

    /**
     * Concurrently probes WEB_PORTS on the given host with a 3 s HTTP timeout each.
     * Returns the first detected panel, or {detected:false} if nothing responds.
     */
    private JsonObject probeWebConsoles(String host) {
        List<CompletableFuture<JsonObject>> futures = new ArrayList<>();
        for (int port : WEB_PORTS) {
            final int p = port;
            futures.add(CompletableFuture.supplyAsync(() -> probePort(host, p)));
        }
        long deadline = System.currentTimeMillis() + 6_000;
        for (CompletableFuture<JsonObject> f : futures) {
            try {
                long rem = deadline - System.currentTimeMillis();
                if (rem <= 0) break;
                JsonObject r = f.get(rem, TimeUnit.MILLISECONDS);
                if (r != null && gBool(r, "detected", false)) return r;
            } catch (Exception ignored) {}
        }
        JsonObject notFound = new JsonObject();
        notFound.addProperty("detected", false);
        return notFound;
    }

    /** HTTP-probe a single port; returns a result object or null on connection failure. */
    private JsonObject probePort(String host, int port) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/"))
                .timeout(Duration.ofSeconds(3))
                .header("User-Agent", UA)
                .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() != null ? resp.body().toLowerCase() : "";

            for (String[] sig : PANEL_SIGS) {
                if (body.contains(sig[0])) {
                    JsonObject r = new JsonObject();
                    r.addProperty("detected",  true);
                    r.addProperty("port",      port);
                    r.addProperty("panelType", sig[1]);
                    r.addProperty("xssNote",   sig[2]);
                    return r;
                }
            }
            // Port open but panel type unknown
            JsonObject r = new JsonObject();
            r.addProperty("detected",  true);
            r.addProperty("port",      port);
            r.addProperty("panelType", "Unknown Web Panel");
            r.addProperty("xssNote",   "Panel type could not be identified. Custom/older panels may be vulnerable to XSS in the console log.");
            return r;
        } catch (Exception e) {
            return null; // connection refused or timed out
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
            o.addProperty("verified",        !VulnDb.isAiSourced(e));
            arr.add(o);
        }
        sendJson(ex, 200, arr);
    }

    // ── POST /api/chat ────────────────────────────────────────────────────

    private void handleChat(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        if (isRateLimited()) { sendText(ex, 429, "Too many requests — try again shortly."); return; }
        try {
            JsonObject body = parseBody(ex);
            String message  = str(body, "message", "").trim();
            if (message.isBlank()) { sendText(ex, 400, "Empty message"); return; }

            if (!AIConfig.INSTANCE.isConfigured()) {
                sendText(ex, 400, "No AI key configured. Add one in Settings.");
                return;
            }

            // Build prompt with recent history context (last 6 turns).
            // Hold the list monitor for the whole compound read — size()+get(i) on a
            // synchronizedList is otherwise racy if another /api/chat request mutates it.
            StringBuilder prompt = new StringBuilder();
            synchronized (chatHistory) {
                int start = Math.max(0, chatHistory.size() - 6);
                for (int i = start; i < chatHistory.size(); i++) {
                    Map<String, String> turn = chatHistory.get(i);
                    prompt.append(turn.get("role")).append(": ").append(turn.get("content")).append("\n");
                }
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

            // Append both turns atomically so concurrent requests can't interleave them
            synchronized (chatHistory) {
                chatHistory.add(Map.of("role", "User",      "content", message));
                chatHistory.add(Map.of("role", "Assistant", "content", reply[0]));
                while (chatHistory.size() > 40) chatHistory.remove(0);
            }

            JsonObject out = new JsonObject();
            out.addProperty("reply", reply[0]);
            sendJson(ex, 200, out);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    // ── POST /api/analyze ─────────────────────────────────────────────────

    private void handleAnalyze(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        if (isRateLimited()) { sendText(ex, 429, "Too many requests — try again shortly."); return; }
        try {
            JsonObject body    = parseBody(ex);
            String     ip      = sanitizeField(str(body, "ip",       "unknown"), 64);
            String     software = sanitizeField(str(body, "software", "unknown"), 64);
            String     version = sanitizeField(str(body, "version",  "unknown"), 32);
            JsonArray  pArr    = body.has("plugins") ? body.getAsJsonArray("plugins") : new JsonArray();
            List<String> plugins = new ArrayList<>();
            for (JsonElement p : pArr) {
                String pname = sanitizeField(p.isJsonPrimitive() ? p.getAsString() : "", 64);
                if (!pname.isBlank()) plugins.add(pname);
            }

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

            String prompt = "Analyse this Minecraft server:\n<server_info>\n" +
                "IP: " + ip + "\nSoftware: " + software + " " + version +
                "\nPlugins: " + String.join(", ", plugins) + "\n" + vulnSummary +
                "\n</server_info>\n\nProvide a concise numbered list of exploits that likely work on this server. " +
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
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    // ── /api/settings ─────────────────────────────────────────────────────

    private void handleSettings(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("GET")) {
            // Never echo secret key material back over the wire. Report only whether each
            // key is set so the UI can show a "saved" state; the values stay server-side.
            JsonObject out = new JsonObject();
            out.addProperty("provider",        AIConfig.INSTANCE.provider);
            out.addProperty("anthropicKeySet", !AIConfig.INSTANCE.anthropicKey.isBlank());
            out.addProperty("openaiKeySet",    !AIConfig.INSTANCE.openaiKey.isBlank());
            out.addProperty("geminiKeySet",    !AIConfig.INSTANCE.geminiKey.isBlank());
            out.addProperty("shodanKeySet",    !AIConfig.INSTANCE.shodanApiKey.isBlank());
            out.addProperty("censysKeySet",    !AIConfig.INSTANCE.censysApiKey.isBlank());
            out.addProperty("fofaKeySet",      !AIConfig.INSTANCE.fofaApiKey.isBlank());
            out.addProperty("model",        AIConfig.INSTANCE.model);
            out.addProperty("maxTokens",    AIConfig.INSTANCE.maxTokens);
            out.addProperty("systemPrompt", AIConfig.INSTANCE.systemPrompt);
            sendJson(ex, 200, out);
        } else if (ex.getRequestMethod().equals("POST")) {
            try {
                JsonObject body = parseBody(ex);
                if (body.has("provider"))     AIConfig.INSTANCE.provider     = str(body, "provider",     AIConfig.INSTANCE.provider);
                // Key fields are only updated when a non-blank value is supplied, so the UI can
                // omit them (or send blank) to keep the existing stored key untouched.
                if (hasValue(body, "anthropicKey")) AIConfig.INSTANCE.anthropicKey = str(body, "anthropicKey", AIConfig.INSTANCE.anthropicKey);
                if (hasValue(body, "openaiKey"))    AIConfig.INSTANCE.openaiKey    = str(body, "openaiKey",    AIConfig.INSTANCE.openaiKey);
                if (hasValue(body, "geminiKey"))    AIConfig.INSTANCE.geminiKey    = str(body, "geminiKey",    AIConfig.INSTANCE.geminiKey);
                if (hasValue(body, "shodanApiKey")) AIConfig.INSTANCE.shodanApiKey = str(body, "shodanApiKey", AIConfig.INSTANCE.shodanApiKey);
                if (hasValue(body, "censysApiKey")) AIConfig.INSTANCE.censysApiKey = str(body, "censysApiKey", AIConfig.INSTANCE.censysApiKey);
                if (hasValue(body, "fofaApiKey"))   AIConfig.INSTANCE.fofaApiKey   = str(body, "fofaApiKey",   AIConfig.INSTANCE.fofaApiKey);
                if (body.has("model"))        AIConfig.INSTANCE.model        = str(body, "model",        AIConfig.INSTANCE.model);
                if (body.has("maxTokens"))    AIConfig.INSTANCE.maxTokens    = gInt(body, "maxTokens", AIConfig.INSTANCE.maxTokens);
                if (body.has("systemPrompt")) AIConfig.INSTANCE.systemPrompt = str(body, "systemPrompt", AIConfig.INSTANCE.systemPrompt);
                AIConfig.save();
                sendJson(ex, 200, new JsonObject());
            } catch (Exception e) {
                ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
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
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    private void handleAltSwitch(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            JsonObject body = parseBody(ex);
            if (!body.has("index")) { sendText(ex, 400, "Missing index"); return; }
            int index = gInt(body, "index", -1);
            if (index < 0) { sendText(ex, 400, "Invalid index"); return; }
            AltManager.INSTANCE.switchTo(index);
            sendJson(ex, 200, new JsonObject());
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
        }
    }

    private void handleAltRestore(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }
        try {
            AltManager.INSTANCE.restore();
            sendJson(ex, 200, new JsonObject());
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[Companion] Handler error: {}", e.getMessage());
            sendText(ex, 500, "Internal server error");
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
            o.addProperty("verified",    !VulnDb.isAiSourced(e));
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
            if (resp.statusCode() == 200) return resp.body();
            // Surface 401/403/429/etc. so a bad key or rate-limit is distinguishable from a network error
            ClaudeMCMod.LOGGER.warn("[Companion] GET {} → HTTP {}", url, resp.statusCode());
            return null;
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

    /** True only when the key is present and holds a non-blank string. */
    private static boolean hasValue(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && !o.get(key).getAsString().isBlank();
    }

    /** Null/type-safe int read — upstream APIs sometimes send a field as a string/object/null. */
    private static int gInt(JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) return def;
        try { return o.get(key).getAsInt(); } catch (NumberFormatException e) { return def; }
    }

    /** Null/type-safe boolean read. */
    private static boolean gBool(JsonObject o, String key, boolean def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) return def;
        try { return o.get(key).getAsBoolean(); } catch (Exception e) { return def; }
    }

    private static String sanitizeField(String s, int maxLen) {
        if (s == null) return "";
        s = s.replaceAll("[\\x00-\\x1F\\x7F]", " ").trim();
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        return s;
    }

    private static boolean isAllowedLookupTarget(String host) {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            return !addr.isLoopbackAddress()
                && !addr.isSiteLocalAddress()
                && !addr.isLinkLocalAddress()
                && !addr.isAnyLocalAddress()
                && !addr.isMulticastAddress();
        } catch (Exception e) {
            return true; // hostname (not bare IP) — let the external API resolve it
        }
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

    private boolean isRateLimited() {
        long now = System.currentTimeMillis();
        apiRateWindow.removeIf(t -> t < now - 60_000);
        if (apiRateWindow.size() >= RATE_LIMIT_RPM) return true;
        apiRateWindow.addLast(now);
        return false;
    }

    private static String generateCsrfToken() {
        byte[] bytes = new byte[24];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
