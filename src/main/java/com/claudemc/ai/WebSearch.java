package com.claudemc.ai;

import com.claudemc.ClaudeMCMod;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight web search layer using DuckDuckGo HTML search.
 * No API key required. Used by ExploitAdvisor to gather recent
 * exploit/CVE information for specific server software and plugins.
 *
 * All requests are async-safe; call from a virtual thread.
 */
public final class WebSearch {

    private WebSearch() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** Snippet extractor patterns for DuckDuckGo HTML results */
    private static final Pattern SNIPPET_PATTERN = Pattern.compile(
        "class=[\"']result__snippet[\"'][^>]*>(.*?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern STRIP_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern COLLAPSE_WS = Pattern.compile("\\s{2,}");

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Searches DuckDuckGo for {@code query} and returns up to {@code maxResults}
     * text snippets. Returns an empty list on network failure.
     */
    public static List<String> search(String query, int maxResults) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // Use DuckDuckGo lite HTML — lighter and easier to parse
            String url = "https://html.duckduckgo.com/html/?q=" + encoded + "&kl=us-en";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                // Provide a browser-like UA to avoid bot-blocks
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/125.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();

            return parseSnippets(resp.body(), maxResults);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[WebSearch] search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds and runs several targeted queries for a given server fingerprint,
     * returning a combined list of unique snippets. Runs queries sequentially to
     * avoid hammering DDG and getting rate-limited.
     *
     * @param software  e.g. "Paper", "Spigot", "PurpurMC"
     * @param mcVersion e.g. "1.21.1" (may be empty)
     * @param plugins   list of detected plugin names
     * @param maxTotal  max snippets across all queries
     */
    public static List<String> searchForExploits(String software,
                                                  String mcVersion,
                                                  List<String> plugins,
                                                  int maxTotal) {
        List<String> all = new ArrayList<>();

        // Primary: software + version
        String baseQuery = software + (mcVersion.isBlank() ? "" : " " + mcVersion)
            + " minecraft exploit dupe vulnerability 2024 2025";
        all.addAll(search(baseQuery, 4));

        // Per-plugin queries (top 3 plugins to keep request count low)
        for (int i = 0; i < Math.min(plugins.size(), 3) && all.size() < maxTotal; i++) {
            String plugin = plugins.get(i);
            // Skip generic/unknown ones
            if (plugin.length() < 3) continue;
            String pluginQuery = plugin + " minecraft plugin exploit dupe vulnerability 2024 2025";
            List<String> pluginResults = search(pluginQuery, 3);
            for (String r : pluginResults) {
                if (!all.contains(r)) all.add(r);
                if (all.size() >= maxTotal) break;
            }
            // Brief pause between requests to be polite
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }

        // dupedb.net targeted search
        if (all.size() < maxTotal) {
            String dupeQuery = "site:dupedb.net " + software + " " + mcVersion;
            List<String> dupeResults = search(dupeQuery, 3);
            for (String r : dupeResults) {
                if (!all.contains(r)) all.add(r);
                if (all.size() >= maxTotal) break;
            }
        }

        return all;
    }

    // ── HTML parsing ─────────────────────────────────────────────────────

    private static List<String> parseSnippets(String html, int max) {
        List<String> results = new ArrayList<>();
        Matcher m = SNIPPET_PATTERN.matcher(html);

        while (m.find() && results.size() < max) {
            String raw     = m.group(1);
            String text    = STRIP_TAGS.matcher(raw).replaceAll(" ");
            text           = COLLAPSE_WS.matcher(text).replaceAll(" ").trim();
            // Decode basic HTML entities
            text = text.replace("&amp;", "&")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&quot;", "\"")
                       .replace("&#x27;", "'")
                       .replace("&nbsp;", " ");
            if (text.length() > 20) {
                // Cap snippet length for prompt economy
                if (text.length() > 220) text = text.substring(0, 217) + "…";
                results.add(text);
            }
        }

        // DDG sometimes uses a different snippet class — fall back to result__body
        if (results.isEmpty()) {
            Pattern fallback = Pattern.compile(
                "class=[\"']result__body[\"'][^>]*>(.*?)</div>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher fm = fallback.matcher(html);
            while (fm.find() && results.size() < max) {
                String text = STRIP_TAGS.matcher(fm.group(1)).replaceAll(" ")
                    .replaceAll("\\s{2,}", " ").trim();
                if (text.length() > 20) {
                    if (text.length() > 220) text = text.substring(0, 217) + "…";
                    results.add(text);
                }
            }
        }

        return results;
    }
}
