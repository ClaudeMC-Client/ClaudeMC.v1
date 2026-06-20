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

    private static final List<VulnEntry> DB = new ArrayList<>(List.of(

        // ── Web console panel XSS (LiveOverflow 2022) ────────────────────

        new VulnEntry("multicraft", Severity.HIGH,
            "all versions with HTML console output",
            "Multicraft web panel renders server chat as innerHTML. Sending a <script> payload in chat "
            + "executes arbitrary JS in the admin's browser, enabling jQuery-based RCON ForceOP.",
            "update panel to use innerText / enable CSP",
            "webconsole-xss"),

        new VulnEntry("amp", Severity.HIGH,
            "AMP pre-2023 builds",
            "AMP (Application Management Panel) jQuery-RCON console: #rconCommand/#sendRconCommand "
            + "selectors allow XSS-based RCON command injection from chat.",
            "2023+ builds with output sanitisation",
            "webconsole-xss"),

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
            "forceopbungee"),

        // ── Additional plugin vulnerabilities ─────────────────────────────

        new VulnEntry("placeholderapi", Severity.MEDIUM,
            "PlaceholderAPI < 2.11.3",
            "Placeholder injection: crafted strings can expose server internals via %server_name% abuse.",
            "2.11.3", null),

        new VulnEntry("griefprevention", Severity.HIGH,
            "GriefPrevention < 16.18.1",
            "Claim bypass: rapid natural-block-break packets near claim border bypass ownership check.",
            "16.18.1", "griefprevention"),

        new VulnEntry("coreprotect", Severity.MEDIUM,
            "CoreProtect < 22.2",
            "Async database write race allows log-rollback desync — exploit with PacketMine rapid break.",
            "22.2", null),

        new VulnEntry("advancedenchantments", Severity.HIGH,
            "AdvancedEnchantments < 9.0.4",
            "NBT enchant dupe: equip → unequip rapid slot-swap causes enchant data to clone.",
            "9.0.4", "advancedenchantments-dupe"),

        new VulnEntry("itemsadder", Severity.HIGH,
            "ItemsAdder < 3.6.0",
            "Custom item dupe via rapid inventory-click on ItemsAdder furniture while sneaking.",
            "3.6.0", "itemsadder-dupe"),

        new VulnEntry("superiorskyblock", Severity.HIGH,
            "SuperiorSkyblock2 < 2.12.0",
            "Island bank race condition: simultaneous withdraw requests return duplicated balance.",
            "2.12.0", "superiorskyblock-bank"),

        new VulnEntry("playershops", Severity.HIGH,
            "PlayerShops (various) any old build",
            "Buy-cancel race: open shop → buy → immediately close screen returns item + charges 0 coins.",
            "Check plugin changelog", "playershops-race"),

        new VulnEntry("chestshop", Severity.MEDIUM,
            "ChestShop < 3.12.2",
            "Chest shop duplication via rapid right-click buying while the server is under lag.",
            "3.12.2", null),

        new VulnEntry("authme", Severity.CRITICAL,
            "AuthMe < 5.6.0 on offline servers",
            "Default password hash (SHA256 unsalted) crackable. /login bypass via hash collision on old builds.",
            "5.6.0", null),

        new VulnEntry("bungeecord-authbypass", Severity.CRITICAL,
            "BungeeCord/Velocity + AuthMe on cracked servers (all versions)",
            "Proxy processes /server before AuthMe authenticates the player. Connecting with a target's username and sending /server <backend> during the login phase bypasses auth entirely — player lands on backend sub-server with target's permissions.",
            "BungeeGuard or IP-whitelist on backends", null),

        new VulnEntry("citizensapi", Severity.MEDIUM,
            "Citizens2 < 2.0.33",
            "NPC right-click command injection: NPC commands run as console on misconfigured servers.",
            "2.0.33", null),

        new VulnEntry("votifier", Severity.CRITICAL,
            "NuVotifier / old Votifier < 2.7.3",
            "Unauthenticated vote packet: any host can send a vote triggering in-game rewards (vote abuse).",
            "2.7.3", null),

        new VulnEntry("multiverse", Severity.HIGH,
            "Multiverse-Core < 4.3.12",
            "World teleport bypass: /mv tp can be abused to enter restricted worlds without permission.",
            "4.3.12", null),

        new VulnEntry("skript", Severity.HIGH,
            "Skript < 2.8.0 with eval",
            "If server uses Skript with eval/parse support, chat injection can execute arbitrary Skript code.",
            "2.8.0 or disable eval", null),

        new VulnEntry("simpleteams", Severity.HIGH,
            "SimpleTeams 2.0.0 (all versions)",
            "Team prefix injection: /team edit prefix accepts raw MiniMessage tags. Setting a prefix with <click:run_command:…> causes the payload to fire for any player who runs /team info <name>. 16-char display limit but no tag stripping.",
            "no patch yet — disable /team edit prefix permissions", null),

        new VulnEntry("zelchat", Severity.HIGH,
            "ZelChat (pre-patch, Java non-vanilla servers)",
            "MiniMessage escape bypass: <<aqua>aqua ><click:run_command:…> payload survives ZelChat's tag-stripping sanitiser. Second-pass bypass (<<click:run_command:…>click:run_command:…>) defeats the follow-up fix.",
            "update ZelChat or apply double-strip patch", null),

        new VulnEntry("discordsrv", Severity.MEDIUM,
            "DiscordSRV with relay to game chat (all versions)",
            "MiniMessage tags sent via a linked Discord channel are passed to in-game chat without sanitisation. Payload: <aqua><click:run_command:/op [username]>[click for free robux]. Requires access to the linked Discord server.",
            "sanitise incoming Discord messages before MiniMessage parsing", null)
    ));

    // Tracks plugin names added by VulnDbUpdater (AI/web-sourced) vs the curated baseline.
    private static final Set<String> AI_SOURCED_PLUGINS = Collections.synchronizedSet(new HashSet<>());

    /** Adds a dynamically discovered entry. Ignored if pluginName already exists in DB. */
    public static synchronized void addDynamic(VulnEntry entry) {
        String lower = entry.pluginName().toLowerCase();
        for (VulnEntry e : DB) {
            if (e.pluginName().toLowerCase().equals(lower)) return;
        }
        DB.add(entry);
        AI_SOURCED_PLUGINS.add(lower);
    }

    /** Returns true when the entry was added by VulnDbUpdater (AI/web-sourced), not the curated baseline. */
    public static boolean isAiSourced(VulnEntry e) {
        return AI_SOURCED_PLUGINS.contains(e.pluginName().toLowerCase());
    }

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

    /** Returns a snapshot of all entries — safe to iterate while updates arrive. */
    public static synchronized List<VulnEntry> all() { return new ArrayList<>(DB); }
}
