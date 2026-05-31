package com.claudemc.server;

import com.claudemc.ClaudeMCMod;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actively probes the server to gather software version and plugin version info
 * that isn't available from passive packet sniffing alone.
 *
 * Probe sequence (each stage times out after ~4 seconds if no response):
 *   1) Parse version from brand string (immediate, passive)
 *   2) Send /version  → capture "This server is running X version Y (MC: Z)"
 *   3) Send /plugins  → capture "Plugins (N): A, B, C, ..."
 *   4) For top plugins, send /version <plugin> → capture version strings
 *
 * All chat captures use Fabric's ClientReceiveMessageEvents.GAME so no mixin needed.
 * The probe is quiet — it doesn't spam visible chat and only runs when explicitly started.
 */
public class ServerProbe {

    public static final ServerProbe INSTANCE = new ServerProbe();

    // ── Detected data ────────────────────────────────────────────────────

    private volatile String detectedSoftware = "";
    private volatile String detectedMcVersion = "";
    /** plugin name → version string (may be empty if we only know the name) */
    private final Map<String, String> pluginVersions = new LinkedHashMap<>();
    private volatile boolean probeComplete = false;

    // ── Probe state machine ───────────────────────────────────────────────

    private enum Stage { IDLE, WAIT_VERSION, WAIT_PLUGINS, DONE }
    private volatile Stage stage = Stage.IDLE;
    private int stageTicksLeft = 0;

    // Commands queued to be sent on the main thread
    private final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();

    // ── Regex patterns ────────────────────────────────────────────────────

    // "This server is running Paper version git-Paper-xxx (MC: 1.21.1)"
    private static final Pattern VERSION_RESPONSE = Pattern.compile(
        "running\\s+(\\S[^v]*)\\s+version\\s+\\S+[^(]*\\(MC:\\s*([\\d.]+)",
        Pattern.CASE_INSENSITIVE);

    // "Plugins (10): EssentialsX, CMI, LuckPerms, ..."
    private static final Pattern PLUGINS_RESPONSE = Pattern.compile(
        "Plugins?\\s*\\(\\d+\\):\\s*(.+)", Pattern.CASE_INSENSITIVE);

    // Plugin colour codes: "§aEssentials §b(2.21.0)"
    private static final Pattern PLUGIN_WITH_VERSION = Pattern.compile(
        "([A-Za-z0-9_\\-]+)\\s*(?:§.)?\\(([\\d.]+(?:-\\w+)*)\\)");

    // Minecraft version in brand string: "Paper 1.21.1" or "git-Paper-xxx 1.21.1-R0.1"
    private static final Pattern BRAND_VERSION = Pattern.compile(
        "(\\d+\\.\\d+(?:\\.\\d+)?)");

    private static final Pattern SOFTWARE_NAMES = Pattern.compile(
        "\\b(Paper|Spigot|CraftBukkit|Purpur|PurpurMC|Airplane|Folia|Pufferfish|Waterfall|" +
        "BungeeCord|Velocity|Fabric|Forge|Mohist|Magma|Sponge|SpongeVanilla|SpongeForge)\\b",
        Pattern.CASE_INSENSITIVE);

    // ── Initialisation ────────────────────────────────────────────────────

    private ServerProbe() {
        // Listen for game messages (server command responses)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || stage == Stage.IDLE || stage == Stage.DONE) return;
            processMessage(message.getString());
        });

        // Reset on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Starts the active probe sequence. Should be called after a brief join delay
     * (so the server has finished sending welcome packets).
     */
    public void startProbe() {
        if (stage != Stage.IDLE) return;
        probeComplete = false;

        // Parse brand immediately (passive, no command needed)
        parseBrand(ServerInfo.INSTANCE.getBrand());

        stage = Stage.WAIT_VERSION;
        stageTicksLeft = 80; // 4 seconds
        commandQueue.add("/version");

        ClaudeMCMod.LOGGER.info("[ServerProbe] Probe started — sending /version");
    }

    /**
     * Called from ModuleManager.onTick / ClaudeMCClient.onTick to drive the state machine
     * and flush the command queue on the main thread.
     */
    public void tick(MinecraftClient client) {
        if (stage == Stage.IDLE || stage == Stage.DONE) return;
        if (client.player == null || client.getNetworkHandler() == null) return;

        // Send queued commands
        String cmd = commandQueue.poll();
        if (cmd != null) {
            client.getNetworkHandler().sendChatCommand(cmd.substring(1)); // strip leading /
        }

        // Timeout to next stage
        if (--stageTicksLeft <= 0) advanceStage(client);
    }

    public void reset() {
        stage = Stage.IDLE;
        stageTicksLeft = 0;
        detectedSoftware = "";
        detectedMcVersion = "";
        pluginVersions.clear();
        probeComplete = false;
        commandQueue.clear();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public String getSoftware()   { return detectedSoftware; }
    public String getMcVersion()  { return detectedMcVersion; }
    public boolean isComplete()   { return probeComplete; }

    /**
     * Returns a full description of detected software for the AI prompt, combining
     * passive brand data with any actively probed data.
     */
    public String buildFullFingerprint() {
        StringBuilder sb = new StringBuilder();
        String brand = ServerInfo.INSTANCE.getBrand();
        sb.append("Server brand (raw): ").append(brand).append("\n");

        if (!detectedSoftware.isBlank())
            sb.append("Software: ").append(detectedSoftware).append("\n");
        if (!detectedMcVersion.isBlank())
            sb.append("Minecraft version: ").append(detectedMcVersion).append("\n");

        List<String> passivePlugins = ServerInfo.INSTANCE.getPlugins();
        if (!passivePlugins.isEmpty()) {
            sb.append("Plugins (from channel registration): ")
              .append(String.join(", ", passivePlugins)).append("\n");
        }

        if (!pluginVersions.isEmpty()) {
            sb.append("Plugins with versions (from /version probes):\n");
            for (var entry : pluginVersions.entrySet()) {
                sb.append("  ").append(entry.getKey());
                if (!entry.getValue().isBlank()) sb.append(" ").append(entry.getValue());
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Returns the best-known list of plugin names (with-version probed first,
     * passive channel list as fallback).
     */
    public List<String> getAllKnownPlugins() {
        if (!pluginVersions.isEmpty()) return new ArrayList<>(pluginVersions.keySet());
        return ServerInfo.INSTANCE.getPlugins();
    }

    // ── State machine ─────────────────────────────────────────────────────

    private void advanceStage(MinecraftClient client) {
        switch (stage) {
            case WAIT_VERSION -> {
                stage = Stage.WAIT_PLUGINS;
                stageTicksLeft = 80;
                commandQueue.add("/plugins");
                ClaudeMCMod.LOGGER.info("[ServerProbe] Advancing to /plugins probe");
            }
            case WAIT_PLUGINS -> {
                stage = Stage.DONE;
                probeComplete = true;
                ClaudeMCMod.LOGGER.info("[ServerProbe] Probe complete — software={} mcVersion={} plugins={}",
                    detectedSoftware, detectedMcVersion, pluginVersions.keySet());
            }
            default -> {}
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────

    private void processMessage(String text) {
        String clean = text.replaceAll("§.", "").trim();

        switch (stage) {
            case WAIT_VERSION -> {
                Matcher vm = VERSION_RESPONSE.matcher(clean);
                if (vm.find()) {
                    detectedSoftware = vm.group(1).trim();
                    detectedMcVersion = vm.group(2).trim();
                    ClaudeMCMod.LOGGER.info("[ServerProbe] Detected software={} MC={}",
                        detectedSoftware, detectedMcVersion);
                    // Immediately advance
                    stageTicksLeft = 1;
                }
            }
            case WAIT_PLUGINS -> {
                Matcher pm = PLUGINS_RESPONSE.matcher(clean);
                if (pm.find()) {
                    parsePluginList(pm.group(1));
                    stageTicksLeft = 1; // move on
                }
            }
            default -> {}
        }
    }

    private void parseBrand(String brand) {
        if (brand == null || brand.isBlank() || "Unknown".equals(brand)) return;

        Matcher sm = SOFTWARE_NAMES.matcher(brand);
        if (sm.find()) detectedSoftware = sm.group(1);

        Matcher vm = BRAND_VERSION.matcher(brand);
        if (vm.find()) {
            // First version-like string in the brand is usually the MC version
            String candidate = vm.group(1);
            if (candidate.startsWith("1.")) detectedMcVersion = candidate;
        }
    }

    private void parsePluginList(String raw) {
        // Strip formatting codes, then split on comma or space-comma
        String clean = raw.replaceAll("§.", "");

        // Try to extract name+version pairs first
        Matcher m = PLUGIN_WITH_VERSION.matcher(clean);
        while (m.find()) {
            pluginVersions.put(m.group(1), m.group(2));
        }

        // Fall back to plain name splitting if no versions found
        if (pluginVersions.isEmpty()) {
            for (String part : clean.split("[,\\s]+")) {
                String name = part.trim().replaceAll("[^A-Za-z0-9_\\-]", "");
                if (name.length() > 2) pluginVersions.put(name, "");
            }
        }

        ClaudeMCMod.LOGGER.info("[ServerProbe] Detected {} plugins from /plugins: {}",
            pluginVersions.size(), pluginVersions.keySet());
    }
}
