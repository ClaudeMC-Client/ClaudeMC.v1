package com.claudemc.server;

import com.claudemc.ClaudeMCMod;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * DupeDB REST API client (dupedb.net).
 *
 * Public endpoint (no auth):   fetchPublicExploits() — 10 most recent verified exploits
 * Authenticated endpoint:      searchExploits()      — full search with version/plugin filters
 *
 * Authentication uses OAuth 2.1 + PKCE (RFC 6749 + RFC 7636 + RFC 8252), implemented
 * natively with Java 21 standard library — no external SDK required.
 *
 * On first use of an authenticated endpoint, opens the user's browser to dupedb.net
 * for a one-time consent flow. Tokens are persisted at config/claudemc/dupedb.json
 * and auto-refreshed (30-day rotating refresh tokens).
 *
 * Prerequisites: register an OAuth app at https://dupedb.net (account settings → OAuth apps)
 * with redirect URI http://127.0.0.1/callback (loopback — no port).
 * Set the app ID in config/claudemc/dupedb.json (default: "claudemc").
 */
public final class DupeDbClient {

    public static final DupeDbClient INSTANCE = new DupeDbClient();

    private static final String BASE = "https://dupedb.net/api";
    private static final Gson   GSON = new Gson();

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private DupeDbClient() {}

    // ── Public API (no auth) ─────────────────────────────────────────────

    /**
     * Returns up to 10 most recent verified exploits from the public endpoint.
     * No authentication required. Returns empty list on any failure.
     */
    public List<DupeEntry> fetchPublicExploits() {
        try {
            String body = httpGet(BASE + "/public/exploits", null);
            return body != null ? parseEntries(body) : List.of();
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Public fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Authenticated API ────────────────────────────────────────────────

    /**
     * Searches verified Java edition exploits. Triggers the OAuth consent flow on first use.
     * Call from a virtual thread — blocks until the user completes browser authorization.
     *
     * @param mcVersion e.g. "1.21.1" (null = no filter)
     * @param software  e.g. "paper"  (null = no filter)
     * @param plugin    e.g. "essentialsx" (null = no filter)
     * @param statusMsg receives progress messages for in-game display (may be null)
     */
    public List<DupeEntry> searchExploits(String mcVersion, String software, String plugin,
                                          Consumer<String> statusMsg) {
        try {
            String token = ensureToken(statusMsg);
            if (token == null) return List.of();

            StringBuilder url = new StringBuilder(BASE + "/exploits/search")
                .append("?edition=java&status=verified&limit=50");
            if (mcVersion != null && !mcVersion.isBlank())
                url.append("&version=").append(enc(mcVersion));
            if (software != null && !software.isBlank())
                url.append("&software=").append(enc(software));
            if (plugin != null && !plugin.isBlank())
                url.append("&plugin=").append(enc(plugin));

            String body = httpGet(url.toString(), token);
            return body != null ? parseEntries(body) : List.of();
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Token management ─────────────────────────────────────────────────

    private String ensureToken(Consumer<String> statusMsg) throws Exception {
        DupeDbConfig cfg = DupeDbConfig.load();

        // Token valid for at least 2 more minutes?
        if (!cfg.accessToken.isBlank()
                && Instant.now().getEpochSecond() + 120 < cfg.tokenExpiresAt) {
            return cfg.accessToken;
        }

        // Try refresh first
        if (!cfg.refreshToken.isBlank()) {
            String refreshed = tryRefresh(cfg);
            if (refreshed != null) return refreshed;
        }

        // Full OAuth flow
        return doOAuthFlow(cfg, statusMsg);
    }

    private String tryRefresh(DupeDbConfig cfg) {
        try {
            String form = "grant_type=refresh_token"
                + "&refresh_token=" + enc(cfg.refreshToken)
                + "&client_id="     + enc(cfg.appId);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/oauth/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            return storeTokens(GSON.fromJson(resp.body(), JsonObject.class), cfg);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Refresh failed: {}", e.getMessage());
            return null;
        }
    }

    // ── OAuth 2.1 + PKCE (RFC 7636 S256, RFC 8252 §7.3 loopback) ────────

    private String doOAuthFlow(DupeDbConfig cfg, Consumer<String> statusMsg) throws Exception {
        // PKCE verifier + challenge
        byte[] vBytes = new byte[32];
        new SecureRandom().nextBytes(vBytes);
        String verifier  = b64url(vBytes);
        String challenge = b64url(MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state     = b64url(new SecureRandom().generateSeed(16));

        // Bind loopback server on ephemeral port (RFC 8252 §7.3)
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(120_000);
            int    port        = ss.getLocalPort();
            String redirectUri = "http://127.0.0.1:" + port + "/callback";

            // Build authorize URL
            String authorizeUrl = "https://dupedb.net/api/oauth/authorize"
                + "?response_type=code"
                + "&client_id="            + enc(cfg.appId)
                + "&redirect_uri="         + enc(redirectUri)
                + "&code_challenge="       + challenge
                + "&code_challenge_method=S256"
                + "&state="                + state;

            if (statusMsg != null)
                statusMsg.accept("§6[DupeDB] §7Opening browser for DupeDB authorization…");
            ClaudeMCMod.LOGGER.info("[DupeDB] Auth URL: {}", authorizeUrl);

            // Open browser
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(authorizeUrl));
                } else {
                    // Headless — print URL so user can open it manually
                    if (statusMsg != null)
                        statusMsg.accept("§6[DupeDB] §7Open in browser: §f" + authorizeUrl);
                }
            } catch (Exception e) {
                ClaudeMCMod.LOGGER.warn("[DupeDB] Could not open browser: {}", e.getMessage());
            }

            // Wait for callback
            String code = null;
            try (Socket conn = ss.accept()) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = conn.getOutputStream();

                String requestLine = reader.readLine();
                // Drain headers
                String headerLine;
                while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {}

                // Parse GET /callback?code=...&state=... HTTP/1.1
                if (requestLine != null && requestLine.startsWith("GET")) {
                    String path  = requestLine.split(" ")[1];
                    int    qMark = path.indexOf('?');
                    if (qMark >= 0) {
                        Map<String, String> params = parseQuery(path.substring(qMark + 1));
                        if (!state.equals(params.get("state"))) {
                            sendHtml(out, "<h2 style='color:red'>Error: state mismatch</h2>");
                            return null;
                        }
                        if (params.containsKey("error")) {
                            sendHtml(out, "<h2>Authorization denied.</h2><p>Close this tab.</p>");
                            return null;
                        }
                        code = params.get("code");
                        sendHtml(out, "<h2 style='color:green'>Authorized!</h2>" +
                            "<p>You can close this tab and return to Minecraft.</p>");
                    }
                }
            }

            if (code == null) return null;

            // Exchange code for tokens
            String form = "grant_type=authorization_code"
                + "&code="          + enc(code)
                + "&redirect_uri="  + enc(redirectUri)
                + "&client_id="     + enc(cfg.appId)
                + "&code_verifier=" + enc(verifier);

            HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/oauth/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

            HttpResponse<String> tokenResp = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenResp.statusCode() != 200) {
                ClaudeMCMod.LOGGER.warn("[DupeDB] Token exchange failed ({}): {}",
                    tokenResp.statusCode(), tokenResp.body());
                return null;
            }

            String token = storeTokens(GSON.fromJson(tokenResp.body(), JsonObject.class), cfg);
            if (token != null && statusMsg != null)
                statusMsg.accept("§a[DupeDB] §7Connected successfully!");
            return token;
        }
    }

    private String storeTokens(JsonObject json, DupeDbConfig cfg) {
        try {
            if (!json.has("access_token") || json.get("access_token").isJsonNull()) {
                ClaudeMCMod.LOGGER.warn("[DupeDB] Token response missing access_token");
                return null;
            }
            cfg.accessToken  = json.get("access_token").getAsString();
            cfg.refreshToken = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                             ? json.get("refresh_token").getAsString() : "";
            long expiresIn   = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;
            cfg.tokenExpiresAt = Instant.now().getEpochSecond() + expiresIn;
            DupeDbConfig.save(cfg);
            return cfg.accessToken;
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[DupeDB] Token store failed: {}", e.getMessage());
            return null;
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────

    private String httpGet(String url, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept",     "application/json")
            .header("User-Agent", "ClaudeMC/1.17");
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> resp = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401) {
            // Token revoked/expired — clear it so next call triggers a fresh flow
            DupeDbConfig cfg = DupeDbConfig.load();
            cfg.accessToken = "";
            DupeDbConfig.save(cfg);
            return null;
        }
        return resp.statusCode() == 200 ? resp.body() : null;
    }

    // ── Parsing ──────────────────────────────────────────────────────────

    private List<DupeEntry> parseEntries(String body) {
        List<DupeEntry> out = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            JsonArray  arr  = root.has("exploits") ? root.getAsJsonArray("exploits") : new JsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                DupeEntry  e = new DupeEntry();
                e.id      = str(o, "id");
                e.name    = str(o, "name");
                e.type    = str(o, "type");
                e.status  = str(o, "status");
                e.plugin  = str(o, "plugin_name");
                e.patched = o.has("marked_patched_at") && !o.get("marked_patched_at").isJsonNull();
                if (!e.name.isBlank()) out.add(e);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            try {
                map.put(URLDecoder.decode(pair.substring(0, eq),   StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static void sendHtml(OutputStream out, String body) throws IOException {
        String html = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n"
            + "<html><body style='font-family:sans-serif;padding:40px;max-width:500px'>"
            + body + "</body></html>";
        out.write(html.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    // ── Data model ───────────────────────────────────────────────────────

    public static class DupeEntry {
        public String  id, name, type, status, plugin;
        public boolean patched;

        /** Derived VulnDb severity based on type + patched status. */
        public VulnDb.Severity severity() {
            if (patched) return VulnDb.Severity.PATCHED;
            return switch (type) {
                case "forceop"  -> VulnDb.Severity.CRITICAL;
                case "dupe"     -> VulnDb.Severity.HIGH;
                default         -> VulnDb.Severity.MEDIUM;
            };
        }
    }
}
