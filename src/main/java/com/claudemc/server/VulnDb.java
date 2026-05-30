package com.claudemc.server;

import java.util.*;

/**
 * Hard-coded vulnerability database sourced from dupedb.net and public CVE/advisory records.
 * Entries are matched case-insensitively against plugin channel namespaces and the server brand.
 *
 * Severity:
 *   CRITICAL — remote code execution / OP escalation
 *   HIGH     — item duplication, inventory manipulation
 *   MEDIUM   — information leak, minor bypass
 *   PATCHED  — was vulnerable, known-fixed version exists
 */
public class VulnDb {

    public enum Severity { CRITICAL, HIGH, MEDIUM, PATCHED }

    public record VulnEntry(
        String pluginName,
        Severity severity,
        String affectedVersions,
        String description,
        String patchedIn,
        String dupedbnRef        // dupedb.net label / slug for browser link
    ) {}

    private static final List<VulnEntry> DB = List.of(

        // ── Dupe exploits (dupedb.net) ────────────────────────────────────

        new VulnEntry("fadah", Severity.HIGH,
            "< 1.4.0",
            "AuctionHouse FADAH: cancel-auction race condition allows item return + listing refund simultaneously.",
            "1.4.0",
            "fadah"),

        new VulnEntry("auctionhouse", Severity.HIGH,
            "< 1.3.6 (Kicjow build)",
            "Window-close race: CloseHandledScreenC2SPacket fires item return before cancel finalises.",
            "1.3.6",
            "auctionhouse"),

        new VulnEntry("essentials", Severity.MEDIUM,
            "EssentialsX < 2.20.0",
            "VanishDetect: EssentialsX vanish suppresses SpawnEntity but leaks EntityPosition packets, "
            + "revealing exact position of /vanished players.",
            "2.20.0",
            "essentialsx-vanish"),

        new VulnEntry("essentialsx", Severity.MEDIUM,
            "EssentialsX < 2.20.0",
            "Same as essentials — EntityPosition packet leak for vanished players.",
            "2.20.0",
            "essentialsx-vanish"),

        new VulnEntry("cmi", Severity.MEDIUM,
            "CMI < 9.4.0",
            "CMI vanish does not suppress move packets for non-OP players on some build ranges.",
            "9.4.0",
            "cmi-vanish"),

        new VulnEntry("shopguiplus", Severity.HIGH,
            "ShopGUI+ < 1.88.0",
            "Buy-back race condition: rapid transaction packets allow items to be bought at free cost "
            + "on servers with async economy hooks.",
            "1.88.0",
            "shopguiplus"),

        new VulnEntry("geyser", Severity.MEDIUM,
            "Geyser-Spigot < 2.2.0",
            "Bedrock client identifier leak — server exposes Java↔Bedrock UUID mapping.",
            "2.2.0",
            null),

        new VulnEntry("luckperms", Severity.MEDIUM,
            "LuckPerms < 5.4",
            "Verbose mode can be triggered by non-OP on some LP builds via chat command injection.",
            "5.4",
            null),

        new VulnEntry("vault", Severity.MEDIUM,
            "Vault any version",
            "No built-in permission validation; exploitable when combined with unpatched economy plugins.",
            "N/A — use with patched economy plugin",
            null),

        // ── Server software ───────────────────────────────────────────────

        new VulnEntry("spigot", Severity.HIGH,
            "Spigot without Paper patches",
            "BookDupe: BookUpdateC2SPacket spam duplication works on vanilla Spigot without Paper's "
            + "packet-rate fix. Also lacks async-safe chest handling (AuctionDupe DoubleCancel works).",
            "Switch to Paper 1.19.3+",
            "bookdupe"),

        new VulnEntry("craftbukkit", Severity.CRITICAL,
            "CraftBukkit any version",
            "No packet-rate limiting, no command-block validation, no async-safe storage. "
            + "ForceOP CommandBlock exploit and BookDupe both highly effective.",
            "Switch to Paper",
            "forceoppacket"),

        new VulnEntry("paper", Severity.PATCHED,
            "Paper 1.19.3+",
            "BookDupe patched. Most async duplication exploits mitigated. "
            + "Command-block OP exploit patched. Still check individual plugin versions.",
            "Already patched (for listed exploits)",
            null),

        new VulnEntry("purpur", Severity.PATCHED,
            "Purpur (all versions)",
            "Inherits all Paper patches. ForceOP command-block exploit patched. "
            + "AH dupes depend on installed plugins, not Purpur itself.",
            "Already patched",
            null),

        new VulnEntry("bungeecord", Severity.HIGH,
            "BungeeCord < 1.20-R0.3 / older builds",
            "ForceOP: ConnectOther plugin-message accepted from any client on misconfigured hubs. "
            + "IP-forwarding misconfiguration allows auth bypass.",
            "Update + set ip_forward correctly",
            "forceopbungee"),

        new VulnEntry("velocity", Severity.PATCHED,
            "Velocity 3.x",
            "Modern forwarding requires signed token — ForceOP plugin-message exploit does not apply.",
            "Already patched",
            null),

        new VulnEntry("waterfall", Severity.HIGH,
            "Waterfall (unmaintained)",
            "Based on old BungeeCord; same plugin-message exploits apply. Project is EOL.",
            "Migrate to Velocity",
            "forceopbungee")
    );

    /** Returns all matching entries for a given plugin/brand name (case-insensitive). */
    public static List<VulnEntry> lookup(String name) {
        if (name == null) return List.of();
        String lower = name.toLowerCase();
        List<VulnEntry> hits = new ArrayList<>();
        for (VulnEntry e : DB) {
            if (lower.contains(e.pluginName()) || e.pluginName().contains(lower)) {
                hits.add(e);
            }
        }
        return hits;
    }

    /** Returns all entries — used to build the full DB list in the UI. */
    public static List<VulnEntry> all() { return DB; }
}
