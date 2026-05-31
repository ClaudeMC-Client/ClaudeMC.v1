package com.claudemc.server;

import com.claudemc.ClaudeMCMod;
import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.ai.WebSearch;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs once at startup on a virtual thread.
 * Queries DuckDuckGo for Minecraft 1.21.x server exploits, feeds results to the
 * configured AI provider, parses the structured response, and injects any new
 * entries into VulnDb. Results are cached for 23 hours so the AI is only called
 * once per day, not on every launch.
 *
 * Requires an AI API key to be configured; silently does nothing if absent.
 */
public final class VulnDbUpdater {

    public static final VulnDbUpdater INSTANCE = new VulnDbUpdater();

    private static final Path CACHE_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/vulndb_cache.txt");

    private static final long CACHE_MAX_AGE_SECONDS = 23 * 3600L;

    // MC version this build targets — keeps AI output version-appropriate
    private static final String MC_VERSION = "1.21.1";

    private VulnDbUpdater() {}

    public void refreshAsync() {
        Thread.ofVirtual().start(this::refresh);
    }

    // ── Main pipeline ────────────────────────────────────────────────────

    private void refresh() {
        try {
            // Load from cache if still fresh
            if (loadFromCache()) return;

            // Nothing to do without an AI key
            if (!AIConfig.INSTANCE.isConfigured()) return;

            // Web search — 3 targeted queries, brief pause between each to avoid DDG rate-limit
            List<String> snippets = new ArrayList<>();
            snippets.addAll(WebSearch.search(
                "minecraft " + MC_VERSION + " server plugin exploit dupe vulnerability 2024 2025", 5));
            Thread.sleep(600);
            snippets.addAll(WebSearch.search(
                "site:dupedb.net minecraft 1.21 2024 2025", 3));
            Thread.sleep(600);
            snippets.addAll(WebSearch.search(
                "minecraft spigot paper bungeecord plugin security bypass CVE 1.21 2025", 4));

            if (snippets.isEmpty()) return;

            // Keep at most 10 snippets to stay within token budget
            String webCtx = String.join("\n",
                snippets.subList(0, Math.min(10, snippets.size())));

            // Swap system prompt, ask AI, restore
            String savedSys = AIConfig.INSTANCE.systemPrompt;
            AIConfig.INSTANCE.systemPrompt =
                "You are a Minecraft server security researcher. Output only structured data — no prose.";

            String prompt =
                "Based ONLY on the web search results below, list server-side vulnerabilities " +
                "and duplication exploits confirmed to work on Minecraft " + MC_VERSION + " " +
                "(Spigot, Paper, BungeeCord, Velocity, or server plugins). " +
                "Do NOT include client-side issues or anything unrelated to " + MC_VERSION + ". " +
                "Do NOT invent entries not supported by the web results. " +
                "For each entry output EXACTLY ONE LINE in this format:\n" +
                "PLUGIN|SEVERITY|AFFECTED_VERSIONS|DESCRIPTION|PATCHED_IN\n" +
                "SEVERITY must be CRITICAL, HIGH, or MEDIUM. " +
                "Output nothing else — no headers, no blank lines, no explanation.\n\n" +
                "Web results:\n" + webCtx;

            CountDownLatch latch   = new CountDownLatch(1);
            List<String>   aiLines = new ArrayList<>();

            AIClient.INSTANCE.ask(prompt,
                resp -> {
                    AIConfig.INSTANCE.systemPrompt = savedSys;
                    Collections.addAll(aiLines, resp.split("\n"));
                    latch.countDown();
                },
                err -> {
                    AIConfig.INSTANCE.systemPrompt = savedSys;
                    ClaudeMCMod.LOGGER.warn("[VulnDb] AI call failed: {}", err);
                    latch.countDown();
                });

            if (!latch.await(40, TimeUnit.SECONDS)) return;

            parseAndInject(aiLines, true);

        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[VulnDb] Auto-update error: {}", e.getMessage());
        }
    }

    // ── Parse + inject ───────────────────────────────────────────────────

    private void parseAndInject(List<String> lines, boolean saveCache) {
        // Build set of already-known plugin names to avoid duplicates
        Set<String> existing = new HashSet<>();
        for (VulnDb.VulnEntry e : VulnDb.all())
            existing.add(e.pluginName().toLowerCase());

        List<String> cacheLines = new ArrayList<>();
        if (saveCache)
            cacheLines.add("# generated=" + Instant.now().getEpochSecond());

        int added = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", 5);
            if (p.length != 5) continue;

            String plugin = p[0].trim();
            String sevStr = p[1].trim().toUpperCase();
            String vers   = p[2].trim();
            String desc   = p[3].trim();
            String patch  = p[4].trim();

            if (plugin.isBlank() || desc.isBlank()) continue;
            if (!sevStr.equals("CRITICAL") && !sevStr.equals("HIGH") && !sevStr.equals("MEDIUM"))
                continue;
            if (existing.contains(plugin.toLowerCase())) continue;

            VulnDb.Severity sev;
            try { sev = VulnDb.Severity.valueOf(sevStr); } catch (Exception x) { continue; }

            VulnDb.addDynamic(new VulnDb.VulnEntry(plugin, sev, vers, desc, patch, null));
            existing.add(plugin.toLowerCase());
            if (saveCache)
                cacheLines.add(plugin + "|" + sevStr + "|" + vers + "|" + desc + "|" + patch);
            added++;
        }

        if (added > 0) {
            ClaudeMCMod.LOGGER.info("[VulnDb] {} dynamic entries added (MC {}).", added, MC_VERSION);
            if (saveCache) writeCache(cacheLines);
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────

    private boolean loadFromCache() {
        try {
            if (!Files.exists(CACHE_PATH)) return false;
            List<String> lines = Files.readAllLines(CACHE_PATH);
            if (lines.isEmpty()) return false;

            String header = lines.get(0);
            if (!header.startsWith("# generated=")) return false;
            long ts = Long.parseLong(header.substring("# generated=".length()).trim());
            if (Instant.now().getEpochSecond() - ts > CACHE_MAX_AGE_SECONDS) return false;

            parseAndInject(lines.subList(1, lines.size()), false);
            return true;
        } catch (Exception e) {
            return false; // stale or corrupt — fall through to fresh fetch
        }
    }

    private void writeCache(List<String> lines) {
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Files.write(CACHE_PATH, lines);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[VulnDb] Cache write failed: {}", e.getMessage());
        }
    }
}
