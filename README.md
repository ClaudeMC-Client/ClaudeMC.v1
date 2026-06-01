# ClaudeMC v1.19.3

<p align="center">
  <img src="https://s6.imgcdn.dev/Y3BMUd.png" alt="ClaudeMC Logo" width="200"/>
</p>

⚠️ **EDUCATIONAL DISCLAIMER**

---

**This tool is provided strictly for EDUCATIONAL PURPOSES ONLY.**

### Authorized Use Only:
- **Testing your own servers** and your organization's systems
- **Testing your own anti-cheat systems** for security vulnerabilities
- **Educational research** and learning about security mechanisms
- **Authorized penetration testing** with explicit written permission from server owners

### Prohibited Use:
- ❌ Using this tool on any server without explicit written authorization from the server owner/operator
- ❌ Unauthorized access to game servers or systems
- ❌ Circumventing anti-cheat systems for competitive advantage on public servers
- ❌ Any activity that violates the terms of service of Minecraft or the target server
- ❌ Commercial exploitation or resale of this tool's capabilities

### Legal Notice:
The author(s) and contributors are **NOT RESPONSIBLE** for any misuse of this tool. Users assume all legal liability for any unauthorized access, damage, or violations of applicable laws. Unauthorized computer access and breach of terms of service may violate criminal and civil laws including the Computer Fraud and Abuse Act (CFAA) in the United States and equivalent laws in other jurisdictions.

**By using this tool, you acknowledge that you have read this disclaimer and agree to use it only for authorized testing of systems you own or have explicit permission to test.**

A **Meteor Client-style** Fabric mod for Minecraft **1.21.1** — with a built-in **AI engine** that analyses the server you're on, searches the web for recent exploits, and tells you exactly what to type.

---

### 🤖 AI-Powered Server Analysis

ClaudeMC connects to **Claude, ChatGPT, or Gemini** (your API key, stored locally) and puts the AI to work the moment you join a server:

1. **Probes the server** — sends `/version` and `/plugins` automatically and parses the responses to identify the exact server software, Minecraft version, and every installed plugin with its version number.
2. **Searches the web** — queries DuckDuckGo for recent CVEs, dupe methods, and exploit write-ups targeting that exact software stack. No API key required for search.
3. **Generates a ranked exploit list** — the AI receives the full server fingerprint plus live web intelligence and produces a numbered list of actionable attacks, each with **verbatim commands and macro strings** ready to paste straight into the game.

Other AI modules: **SmartReply** generates human-sounding AFK replies so staff checks bounce off you; **AIAssist** lets you type `!ai <question>` in chat for instant local answers without the server seeing it.

> Configure your API key in-game: press `.` → click **`[AI]`** in the ClickGUI footer. Supports Anthropic, OpenAI, and Google Gemini.

---

Beyond AI, ClaudeMC is a full-featured hack client: ESP through walls, projectile trajectories, survival flight, KillAura, AutoCrystal, OreESP, dupe exploits, staff-detection AFK bypass, and 60+ other modules. See the [Module Reference](#module-reference) below.

> **What's new in v1.19.3:** Crash-fix release (last 1.21.1-compatible build). Fixes three mixin failures that prevented Minecraft from launching: `ScreenMixin.charTyped` (no valid injection target — `Screen` never overrides the `Element.charTyped` default, now handled via a chained GLFW char callback), `TitleScreenMixin` `@Shadow` on `addDrawableChild`/`width` (both declared in the parent `Screen` — the mixin now extends `Screen`), and `ClientPlayNetworkHandlerMixin.onCustomPayload` (wrong parameter type — now `CustomPayload`). Minecraft 1.21.11 support lands in v1.20.0. **v1.19.2:** Bugfix release. Fixes operator-precedence bug in `AntiSpam` ad-filter where `&&` bound tighter than `||`, causing the `discord.gg` exception (`!lower.contains("server")`) to never apply. Fixes NPE in `AutoRespawn.onTick` — `getNetworkHandler()` can return null between disconnect and screen transition. Fixes two NPEs in `ElytraFlight.onTick` — both `START_FALL_FLYING` packet sends now guard `getNetworkHandler()`. **v1.19.1:** Bugfix release. Fixes a race condition where concurrent AI calls could corrupt each other's system prompt (affected SmartReply, ExploitAdvisor, ServerFinder, AutoMine, Companion Chat/Analyze). `AIClient.ask()` now accepts an explicit system-prompt parameter so modules never mutate global config. Fixes NPE on `handleAltSwitch` missing index, thread-safety of companion chat history, volatile correctness of `CompanionServer.started`/`boundPort`, and the companion browser always opening port 8080 even when a different port was bound. Fixes `AIClient` crashing on empty Anthropic/OpenAI/Gemini response arrays. Fixes unreachable dead-code branch in `ClickGui` header click. Fixes `DupeDbClient.storeTokens` NPE on missing token fields. **v1.19.0:** ClaudeMC Companion — a browser-based companion app at `localhost:8080`, launched from a new `[ClaudeMC]` button on the Minecraft main menu. Tabs: **Scanner** (MCScans + mcsrvstat.us + mcstatus.io + Shodan, all in one), **Chat** (AI conversation with markdown rendering and 6-turn history), **Settings** (all API keys including Shodan editable in the browser before entering a game), **Alts** (full alt account management), **VulnDb** (searchable/filterable vulnerability database). **v1.18.1:** MiniMessageExploit expanded with four new techniques and three VulnDb entries. **v1.18:** Full in-game settings editing — every setting is editable directly in the ClickGUI; no file editing ever needed. ServerFinder AI Search mode. v1.17 added cross-reference tags. v1.16 added AutoMine evasion overhaul and ForeachCmd/AutoAuth/BookColors/AutoReconnect. v1.13 added the AI layer.

---

## Table of Contents

1. [Requirements](#requirements)
2. [Installation](#installation)
3. [Building from Source](#building-from-source)
4. [In-Game Controls](#in-game-controls)
5. [HUD Overlay](#hud-overlay)
6. [ClickGUI Guide](#clickgui-guide)
7. [Module Reference](#module-reference)
   - [Combat](#combat-modules)
   - [Movement](#movement-modules)
   - [Player](#player-modules)
   - [Render / ESP](#render--esp-modules)
   - [World](#world-modules)
   - [Misc / Exploits](#misc--exploit-modules)
8. [AI Integration](#ai-integration)
   - [AI Settings](#ai-settings)
   - [SmartReply](#smartreply)
   - [ExploitAdvisor](#exploitadvisor)
   - [AIAssist](#aiassist)
9. [ClaudeMC Companion (localhost web app)](#claudemc-companion-localhost-web-app)
   - [Scanner](#scanner)
   - [Chat](#chat)
   - [Settings](#companion-settings)
   - [Alts](#companion-alts)
   - [VulnDb](#companion-vulndb)
10. [AutoMine — Human-like Strip Mining](#automine--human-like-strip-mining)
11. [ServerFinder — Scan for Vulnerable / P2W Servers](#serverfinder--scan-for-vulnerable--p2w-servers)
12. [Cracked Minecraft (TLauncher etc.)](#cracked-minecraft-tlauncher-etc)
13. [Editing Module Settings](#editing-module-settings)
14. [Trajectories — Projectile Prediction](#trajectories--projectile-prediction)
15. [BlockESP — Custom Blocks](#blockesp--custom-blocks)
16. [RecordProof — Screen Capture Hiding](#recordproof--screen-capture-hiding)
17. [Dupe Shortcuts (dupedb.net)](#dupe-shortcuts-dupedbnets)
18. [Troubleshooting](#troubleshooting)

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft Java Edition | **1.21.1** |
| Fabric Loader | ≥ 0.16.5 |
| Fabric API | 0.100.7+1.21.1 (bundled with mod release) |
| Java | **21** or newer |
| OS (RecordProof) | Windows 10 v2004+ / Windows 11 |

---

## Installation

### Step 1 — Install Fabric Loader

1. Download the Fabric Installer from [fabricmc.net/use](https://fabricmc.net/use/).
2. Run the installer, select **Minecraft 1.21.1**, click **Install**.
3. A new Fabric profile appears in the vanilla launcher.

### Step 2 — Install Fabric API

1. Download **Fabric API** for 1.21.1 from [modrinth.com/mod/fabric-api](https://modrinth.com/mod/fabric-api).
2. Place the `.jar` into your `.minecraft/mods/` folder.

### Step 3 — Install ClaudeMC

1. Go to the [Releases page](https://github.com/l0azathkamil/ClaudeMC.v1/releases).
2. Under the latest release, download the **`claudemc-X.X.X.jar`** file (not the Source code zip/tar.gz — those are the raw source, not a runnable mod).
3. Place the `.jar` into `.minecraft/mods/`.
4. Launch Minecraft with the **Fabric 1.21.1** profile.
5. You should see `ClaudeMC v2 initialised` in the log.

> **Why zip/tar.gz?** Older releases (v1.1–v1.4) were published before the CI pipeline was set up to compile the mod. From v1.5 onwards, every release attaches the compiled `.jar` automatically.

---

## Building from Source

> **You don't need to do this to use the mod.** Just download the `.jar` from the [Releases page](https://github.com/l0azathkamil/ClaudeMC.v1/releases) and drop it in `mods/`. The steps below are only needed if you want to modify the code and compile it yourself.

### What you need

| Tool | Where to get it | Notes |
|---|---|---|
| **JDK 21** (full kit, not JRE) | [adoptium.net](https://adoptium.net/) | Must be 21 — Java 17 or 22 won't work |
| **Git** | [git-scm.com](https://git-scm.com/) | To clone the repo |
| Internet connection | — | Downloads ~250 MB of Fabric + Minecraft on first build |

You do **not** need to install Gradle separately. The repo includes `gradlew` / `gradlew.bat` which auto-downloads the right Gradle version.

### Step-by-step

**1. Install JDK 21**

Download the Temurin 21 installer from [adoptium.net](https://adoptium.net/). Run it and make sure `JAVA_HOME` is set (the Temurin installer does this automatically on Windows).

Verify:
```
java -version
```
You should see `openjdk 21`.

**2. Clone the repo and switch branch**

```bash
git clone https://github.com/l0azathkamil/ClaudeMC.v1.git
cd ClaudeMC.v1
git checkout claude/serene-volta-eY5Oe
```

**3. Build**

The first build downloads all Minecraft and Fabric dependencies (~250 MB). This takes 5–10 minutes on a normal connection. Subsequent builds are fast.

```bash
# Windows (Command Prompt or PowerShell):
gradlew.bat build

# macOS / Linux:
./gradlew build
```

If Gradle complains about permissions on macOS/Linux:
```bash
chmod +x gradlew
./gradlew build
```

**4. Find the compiled JAR**

After a successful build, your file is at:
```
build/libs/claudemc-1.19.1.jar
```
(There will also be a `claudemc-1.10.0-sources.jar` — ignore that one.)

**5. Install it**

Copy the JAR to your mods folder:

```bash
# Windows
copy build\libs\claudemc-1.19.1.jar %APPDATA%\.minecraft\mods\

# macOS
cp build/libs/claudemc-1.19.1.jar ~/Library/Application\ Support/minecraft/mods/

# Linux
cp build/libs/claudemc-1.19.1.jar ~/.minecraft/mods/
```

Also make sure you have [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 in your mods folder.

### Common build errors

| Error message | Cause | Fix |
|---|---|---|
| `'java' is not recognized` / `java: not found` | JDK not installed or not on PATH | Install JDK 21 from adoptium.net; restart your terminal |
| `JAVA_HOME is set to an invalid directory` | Wrong JDK path | Update `JAVA_HOME` to point to JDK 21 (e.g. `C:\Program Files\Eclipse Adoptium\jdk-21...`) |
| `Plugin not found: fabric-loom` | Blocked internet or Maven outage | Check you can reach `maven.fabricmc.net`; try again |
| `Could not resolve net.fabricmc:yarn` | Same — Maven unreachable | Retry; check network/firewall |
| `error: release version 21 not supported` | You ran build with Java 17 or older | Set `JAVA_HOME` to JDK 21 |
| `BUILD SUCCESSFUL` but no JAR found | Very unlikely — check `build/libs/` | Run `gradlew build --info` for detail |

---

## In-Game Controls

| Key | Action |
|---|---|
| **`.`** (full stop, rebindable) | Open / close the ClickGUI |
| **Esc** | Close the ClickGUI / cancel keybind listen |
| **Left-click module** | Toggle module on / off |
| **Right-click module** | Expand / collapse module settings |
| **Drag panel header** | Move that category panel |
| **Module hotkey** | Toggle that module instantly (set in Keybinds screen) |

> Tip: Panels remember their positions across sessions (stored per-run).

---

## Custom Keybinds

Press **`.`** → click **[Keybinds]** in the bottom-right corner of the GUI.

| Action | How |
|---|---|
| Set a module hotkey | Click the module row, then press any key |
| Change the GUI open key | Click the **Open GUI** row (top, marked orange), then press any key |
| Clear a bind | Click the row, then press **Delete** or **Backspace** |
| Cancel without changing | Press **Esc** while a row is listening |

Bound keys are shown as a small grey `[KEY]` hint next to each module name in the panels.  
All binds are saved to `.minecraft/config/claudemc/keybinds.json` automatically.

---

## HUD Overlay

The HUD is always visible in-game (not shown in F3 debug view).

```
┌─────────────────────────────────────────────────────────┐
│ ClaudeMC v2          [top-left watermark]               │
│                                                         │
│                                   KillAura   [red]  ←  │
│                                   Flight     [blue] ←   │
│                                   ESP        [green]←   │
│                                                         │
│ FPS 120  TPS 20.0  Ping 14ms      [bottom-left stats]  │
│ XYZ 128.0 / 64.0 / -256.0                              │
│ Nether 16.0 / -32.0                                     │
│ Biome: forest                                           │
│                        [helmet][chest][legs][boots] ←   │
└─────────────────────────────────────────────────────────┘
```

- **Right side** — enabled module list, one per line, coloured by category (Meteor style).
- **Bottom-left** — XYZ coords + opposite-dimension coords + biome.
- **Bottom stats** — FPS, TPS (measured from server time packets), and your ping.
- **Bottom-right** — equipped armour pieces with durability percentage.

---

## ClickGUI Guide

Press **`.`** to open the GUI. Six draggable panels appear — one per category.

```
┌ Combat ──────────┐  ┌ Movement ────────┐  ...
│ ● KillAura       │  │ ○ Flight         │
│ ● Velocity       │  │ ● Speed          │
│ ○ AutoTotem      │  │ ○ NoFall         │
│ ○ Criticals      │  └──────────────────┘
└──────────────────┘
```

- **Green dot** = module is enabled.
- **Grey dot** = module is disabled.
- **Right-click** a module to reveal its settings inline:

```
│ ● KillAura               │
│   Range: 4.0             │  ← left-click = increase, right-click = decrease
│   Filter: Hostile        │  ← click to cycle options
│   Rotate: true           │  ← click to toggle
```

> **All settings are editable directly in the GUI (v1.18+).** Expand a module (right-click it), then interact with any setting:
> - **Numbers** — left-click increases, right-click decreases
> - **Toggles** — either click flips on/off
> - **Options** — left-click = next, right-click = previous
> - **Text fields** (yellow `§e`) — left-click to enter edit mode, type freely, **Enter** or click away to save, **Esc** to cancel, right-click to clear
>
> All values are saved to `config/claudemc/modules.json` and restored on the next launch. See [Editing Module Settings](#editing-module-settings) for details.

---

## Module Reference

### Combat Modules

| Module | Description | Key Settings |
|---|---|---|
| **AimAssist** | Smoothly rotates toward the nearest valid target | Range (1–60), Smoothing (0.01–1.0), Target (Players/Hostile+Players/Hostile/All) |
| **KillAura** | Auto-attacks nearby entities each tick | Range (blocks), Target (Hostile+Players/Players/Hostile/All), Rotate |
| **Velocity** | Reduces knockback received when hit | H-Mult (0=none), V-Mult (1=normal) |
| **AutoTotem** | Moves Totem of Undying to offhand automatically | — |
| **Criticals** | Makes every swing a critical hit (tiny hop) | Mode (Jump/Packet) |
| **AutoCrystal** | Places and detonates end crystals on nearby players | Range, MinDamage, AutoSwitch, AntiSuicide |
| **Surround** | Places obsidian around your feet against crystal explosions | Material, Center |
| **TriggerBot** | Attacks when crosshair is on a valid target | Target, Delay |
| **Reach** | Extends melee and block interaction range | AttackReach, BlockReach |
| **AntiBot** | Filters bot entities from targeting | FilterTablist, FilterNoPing, FilterInvalid |

### Movement Modules

| Module | Description | Key Settings |
|---|---|---|
| **Flight** | Creative-style flight in any game mode | Speed, Mode (Vanilla/Packet) |
| **Speed** | Move horizontally faster | Speed (blocks/tick) |
| **NoFall** | Cancels fall damage | — |
| **Jesus** | Walk on water (and optionally lava) | Lava (true/false) |
| **Step** | Step up full blocks instantly | Height |
| **Sprint** | Always sprint, even sideways | Mode (Forward/Omni) |
| **Scaffold** | Places blocks under your feet automatically | — |
| **SafeWalk** | Prevents walking off edges | — |
| **ElytraFlight** | Boost/Packet/Pitch elytra flight control | Mode, Speed |
| **PacketFly** | Bypasses basic anti-fly via alternating ground-state packets | Speed, Height |
| **InventoryMove** | WASD movement while GUI is open | Sprint, Jump |
| **BunnyHop** | Auto-jumps on landing to keep sprint speed | SpeedBoost |

### Player Modules

| Module | Description | Key Settings |
|---|---|---|
| **AutoEat** | Eats food when hunger drops below threshold | Threshold (0–20) |
| **FastBreak** | Removes mining animation delay | — |
| **ChestStealer** | Shift-clicks all items from open chests | Delay (ticks) |
| **AntiHunger** | Prevents sprint-exhaustion hunger drain | — |
| **AutoArmor** | Equips the best armour from your inventory | — |
| **AutoFish** | Reels in and recasts on fish bite automatically | RecastDelay |
| **AutoFarm** | Harvests mature crops and replants seeds | Radius |
| **FastPlace** | Removes the 4-tick block placement cooldown | — |
| **NoMiningFatigue** | Removes Mining Fatigue effect client-side | — |
| **InvManager** | Auto-drops junk items from inventory | DropJunk, Delay |

### Render / ESP Modules

| Module | Description | Key Settings |
|---|---|---|
| **ESP** | Coloured entity outlines through walls | Filter (All/Players/Hostile) |
| **BlockESP** | Highlights shulkers, chests, spawners + custom blocks | Radius |
| **StorageESP** | Shows container fill level through walls | Radius, ShowFull, ShowEmpty |
| **Tracers** | Lines from screen centre to entities | Filter, Range |
| **Trajectories** | Predicts arrow/throwable flight paths | Self, Others |
| **Fullbright** | Maximum light everywhere | — |
| **FreeCam** | Detach camera from body | Speed |
| **Nametags** | Health/ping/distance above player heads | Health, Ping, Dist |
| **AntiInvis** | Renders invisible and vanished players | Opacity |
| **HoleESP** | Highlights safe holes for crystal combat | Radius, BedrockOnly |
| **OreESP** | X-ray ore scanner | Radius, Tier (All/Valuable/Diamond+/AncientDebris) |
| **Chams** | Entity hitboxes through walls with solid tint | Filter, Alpha |
| **Breadcrumbs** | Position trail showing your path | MaxPoints, MinDist |
| **LogoutSpots** | Marks where players logged out | — |
| **Zoom** | Scrollable camera zoom | Factor (1.5–20×) |
| **Radar** | HUD minimap with player/mob dots | Range, Size, Anchor |
| **TimeChanger** | Locks client-side time (visual only) | Time (Day/Noon/Sunset/Night/Midnight/Custom) |
| **WeatherChanger** | Locks client-side weather (visual only) | Weather (Clear/Rain/Thunder) |
| **NoRender** | Suppress HUD elements | Totem, Fire, BossBar, Scoreboard, PotionHUD, Particles |

#### Colours

| Entity type | ESP colour |
|---|---|
| Players | Red |
| Hostile mobs | Orange |
| Passive mobs | Green |
| Shulker boxes | Purple |
| Chests / barrels | Gold |
| Spawners | Bright red |
| Vanished players | Magenta |

### World Modules

| Module | Description | Key Settings |
|---|---|---|
| **Nuker** | Breaks blocks around you automatically | Radius, Mode (All/Flat/Above) |
| **Timer** | Speed up or slow down game time | Speed (1.0=normal, 2.0=double) |
| **VeinMiner** | Breaks entire ore veins when you mine one block | MaxBlocks |
| **PacketMine** | Instant mine attempt via simultaneous start/stop packets | OnlyInstant |
| **AutoBuild** | Places blocks in Floor/Bridge/Column shapes | Shape, Radius |

### Misc / Exploit Modules

| Module | Description |
|---|---|
| **AutoRespawn** | Instantly respawns when you die |
| **BookDupe** | Sends duplicate book-sign packets (dupedb.net) |
| **AuctionDupe** | Automates auction-house dupe exploits — see below |
| **VanishDetect** | Marks players in tab list with no world entity |
| **RecordProof** | Hides window from Discord/OBS (Windows only) |
| **NoPacketKick** | Suppresses invalid-packet kick attempts |
| **ForceOP** | Fires multiple OP-grant techniques on vulnerable servers |
| **ForceCreative** | Spoofs abilities packet + client game-mode for Creative |
| **AutoReply** | Auto-replies to AFK-check / DM messages with configurable responses |
| **AntiAFK** | Detects vanished staff nearby, sudden TPs, and AFK-check DMs — triggers human-like look-around and notification |
| **NameSpoof** | Client-side display name override (cosmetic) |
| **ChatSpammer** | Sends a message or command on a tick interval |
| **PacketLogger** | Logs incoming chat/game packets to the mod logger |
| **AntiSpam** | Filters duplicate and ad messages from chat |
| **FakePlayer** | Spawns a client-side fake player entity at your position |
| **SmartReply** | AI-generated AFK / DM replies that sound human; falls back to canned response if no key is set |
| **ExploitAdvisor** | Probes server software + plugins, searches web for recent CVEs/dupes, and asks AI to produce verbatim macro-ready exploit instructions |
| **AIAssist** | `!ai <question>` chat helper (intercepted locally) + optional packet narration via PacketLogger |
| **AutoMine** | Human-like strip miner: mines forward, branches left/right, deliberate ore misses, random 1–60s still break on staff detection (no camera movement), elevated miss rate post-detection |
| **ServerFinder** | Queries mcscans.fi for live servers; filters by vulnerability (VulnDb) and/or P2W/gambling status; cross-tags servers that are both exploitable and P2W |
| **ForeachCmd** | Runs a configurable command once per online player (`%player%`) or N times (`%i%`) with random tick delays |
| **AutoAuth** | Auto-sends your password on AuthMe / NLogin / FastLogin / JPremium login prompts |
| **BookColors** | Translates `&x` colour codes to `§x` Minecraft format in book text |
| **AutoReconnect** | Reconnects to the last server after disconnect, after a random configurable delay; optionally rotates to an offline alt first |
| **AuthMeBypass** | Bypasses AuthMe login on cracked BungeeCord servers — sends `/server <backend>` via the proxy during the auth phase, before AuthMe can block you |

---

## AI Integration

ClaudeMC v1.13+ embeds an AI layer that connects to **Anthropic Claude**, **OpenAI GPT**, or **Google Gemini**. All three providers are supported; you choose which one to use and supply your own API key. Keys are stored locally in `config/claudemc/ai.json` and are never transmitted anywhere other than the chosen AI provider.

### AI Settings

Press **`.`** → click **`[AI]`** in the ClickGUI footer.

| Field | Notes |
|---|---|
| **Provider** | Click to cycle: Anthropic → OpenAI → Gemini |
| **Anthropic key** | API key from console.anthropic.com |
| **OpenAI key** | API key from platform.openai.com |
| **Gemini key** | API key from aistudio.google.com |
| **Model** | Leave blank for sensible defaults (`claude-haiku-4-5-20251001` / `gpt-4o-mini` / `gemini-1.5-flash`) |
| **Max tokens** | Response length cap (default 300) |
| **Test Connection** | Sends a live ping to verify the key works |

Press **Tab** to cycle between input fields. The key fields show masked characters (`•••`) in the panel header but reveal what you type.

### SmartReply

**Module:** Misc → `SmartReply`

Replaces AutoReply's hardcoded responses with AI-generated replies that sound like a real player. Triggers on AFK-check patterns in incoming chat and (optionally) any DM.

| Setting | Default | Effect |
|---|---|---|
| `DelayTicks` | 40 (2 s) | How long to wait before sending the AI reply (natural timing) |
| `AnyDM` | off | Trigger on any incoming private message, not just AFK checks |

Falls back to a natural-sounding response (`"yeah im here, what's up"`) if no AI key is configured or if the API call fails — never an obvious `"AFK, brb"` that gives the game away.

### ExploitAdvisor

**Module:** Misc → `ExploitAdvisor`

Three-stage pipeline that runs automatically after joining a server:

**Stage 1 — Active server probe**  
Sends `/version` and `/plugins` and parses the text responses to extract server software, exact Minecraft version, and a full plugin list with version numbers. Falls back to passive brand-string and plugin-channel detection.

**Stage 2 — Web search**  
Queries DuckDuckGo (no API key required) for recent CVEs, duplication glitches, and exploit write-ups targeting the exact software stack detected. Runs separate queries for the top three plugins and searches dupedb.net. Returns up to 12 text snippets.

**Stage 3 — AI synthesis**  
Feeds the full server fingerprint + web snippets to the AI, which produces a numbered list (max 10) of specific, actionable exploits. Each entry includes:
- What the exploit achieves
- Exact step-by-step instructions
- **Verbatim commands and macro strings** ready to paste into a `/macro` or type in chat
- Items or conditions required

| Setting | Default | Effect |
|---|---|---|
| `AutoOnJoin` | on | Fire the pipeline automatically after joining |
| `WaitTicks` | 160 (8 s) | Delay before starting (lets server load finish) |
| `WebSearch` | on | Run DuckDuckGo queries before the AI call |
| `ProbeServer` | on | Send `/version` + `/plugins` to gather software info |

Re-trigger manually at any time via the module's right-click menu → the pipeline resets and reruns.

### AIAssist

**Module:** Misc → `AIAssist`

Two features in one:

**Chat assistant:** Type `!ai <your question>` in the chat box. The message is intercepted before it reaches the server — the AI's answer appears in your local chat only (the server never sees it).

**Packet narration:** When **PacketLogger** is also enabled, AIAssist periodically sends the last 20 logged packets to the AI and prints a plain-English summary of what the server is doing — useful for spotting unusual behaviour or understanding a server's anti-cheat.

| Setting | Default | Effect |
|---|---|---|
| `Prefix` | `!ai` | Trigger prefix for the chat assistant |
| `PacketNarration` | off | Enable periodic packet summary |
| `NarrateTicks` | 200 (10 s) | How often to send the packet batch to AI |

---

## ClaudeMC Companion (localhost web app)

ClaudeMC v1.19 ships an embedded web server that starts automatically when Minecraft launches and serves a companion app at **`http://localhost:8080`**.

Open it from the **Minecraft main menu** — a `[ClaudeMC]` button appears in the top-right corner of the title screen. Click it and your browser opens the companion. It stays running the entire session, so you can switch between Minecraft and the browser tab freely.

The companion gives you a full scanner, AI chat, settings editor, alt manager, and vulnerability database — all accessible *before* you've even picked a server to join.

---

### Scanner

Discover and analyse servers without being in-game.

**Filter row:**
| Control | Options |
|---|---|
| Software | All / paper / spigot / bungeecord / velocity / craftbukkit / mohist / magma |
| Auth Mode | Any / Offline / Online |
| Max Results | Number input (default 50) |
| Include Shodan | Checkbox — requires a Shodan API key in Settings |

**Buttons:**
- **Scan MCScans** — queries `api.mcscans.fi` for live servers, cross-references each result against VulnDb, and shows severity badges (CRITICAL / HIGH / MEDIUM / PATCHED).
- **Shodan Search** — free-text query (e.g. `port:25565 minecraft 1.21`) sent to the Shodan API; results are also VulnDb-enriched.

**Results table columns:** IP:Port | Software | Version | Players | Auth | Vulnerabilities | Actions

**Per-row actions:**
- **Copy IP** — copies the address to clipboard.
- **Enrich** — fetches data from mcsrvstat.us *and* mcstatus.io concurrently, expanding the row inline with MOTD, version, player list, and detected plugins.
- **Analyze** — sends the server's fingerprint to the AI, which returns a ranked exploit plan with verbatim commands.

---

### Chat

Full AI conversation without entering a game.

- Scrollable history with user bubbles (right, blue) and AI bubbles (left, dark).
- Basic markdown rendering: `**bold**`, `` `code` ``, newlines.
- **Enter** to send; the last 6 message pairs are included as context.
- "Thinking…" indicator while the AI responds.
- Works with whatever provider/key is set in Settings — no in-game configuration needed first.

---

### Companion Settings

All API keys and AI settings are editable here without touching any file or being in-game.

| Field | Notes |
|---|---|
| **Provider** | Radio: Anthropic / OpenAI / Gemini |
| **Anthropic key** | Stored in `config/claudemc/ai.json` |
| **OpenAI key** | Stored in `config/claudemc/ai.json` |
| **Gemini key** | Stored in `config/claudemc/ai.json` |
| **Shodan API key** | Used by the Scanner's Shodan search; stored in `config/claudemc/ai.json` |
| **Model** | Leave blank for provider default |
| **Max tokens** | Response length cap |
| **System prompt** | Default prompt prepended to all AI requests |

Click **Save** to persist immediately. Changes take effect on the next AI call — no restart required.

---

### Companion Alts

Manage accounts without opening the in-game GUI.

- **Playing as:** banner shows your current username with a **Restore** button if you are on an alt.
- **Alt list** — each row has a **Switch** button (reconnect required) and a **Delete** button.
- **Add Offline Alt** — enter a username, click Add. Works on cracked/offline servers.
- **Add Session Alt** — enter a username, UUID, and Microsoft access token for online-mode servers.

---

### Companion VulnDb

Browse and search the full vulnerability database.

- Loaded once when you first open the tab.
- **Search** filters by plugin name, description, or affected versions.
- **Severity** dropdown filters to CRITICAL / HIGH / MEDIUM / PATCHED.
- Entry count shown above the table.
- Severity cells are colour-coded identically to the Scanner badges.

---

## DupeDB Integration

ClaudeMC connects to **[dupedb.net](https://dupedb.net)** — a community-maintained database of verified Minecraft duplication exploits and vulnerabilities — to keep VulnDb and ExploitAdvisor current without requiring a mod update.

### How it works

**On every launch** (once per 23 hours), two sources are queried in the background:

1. **Public feed (no auth required)** — `GET /api/public/exploits` returns the 10 most recently verified exploits. Any entry with a known plugin name is added to VulnDb immediately.

2. **AI + web search** — DuckDuckGo queries scoped to Minecraft 1.21.x feed into the AI, which structures confirmed exploits into VulnDb entries. Requires an AI API key.

**When ServerFinder or ExploitAdvisor runs**, the authenticated DupeDB search API (`/api/exploits/search?version=1.21.1&status=verified`) is queried for results specific to the server's detected software stack, giving you community-reported dupes and exploits relevant to that exact server.

### Connecting your DupeDB account (optional — enables full search)

The public feed requires no setup. For authenticated search (broader results, version/plugin filters):

1. Create an account at [dupedb.net](https://dupedb.net).
2. Go to **Account Settings → OAuth Apps** → **Create App**.
3. Set the App ID to `claudemc`, Name to anything, Redirect URI to `http://127.0.0.1/callback`, and tick **Read-Only**.
4. The next time a module triggers an authenticated DupeDB call, your browser will open automatically for a one-time consent. Click **Allow** and close the tab — you're done.

Tokens are stored at `.minecraft/config/claudemc/dupedb.json` and auto-refreshed (30-day rotating tokens). You only authorize once unless you revoke the app.

> To use a different App ID, edit `config/claudemc/dupedb.json` and change `"appId"` before authorizing.

---

## AutoMine — Human-like Strip Mining

**Module:** Misc → `AutoMine`

Automatically runs a strip mine that looks like a real player dug it: it mines forward, branches off to the sides, collects ores it "notices", but deliberately misses a configurable percentage of them. It also freezes completely if **AntiAFK** detects staff nearby so there is no suspicious activity during a check.

### Pattern

```
Main tunnel →→→→→→→→→→→→→→→→→→→→→→→→→→→
                   ↑ branch left (8 blocks)
                              ↑ branch right (8 blocks)
                                         ↑ branch left …
```

1. Face the direction you want to mine before enabling — the module snaps your yaw to the nearest cardinal.
2. Mines a 1-wide × 2-tall forward corridor.
3. Every **BranchEvery** blocks it turns 90° and mines a **BranchLen**-block side branch.
4. Returns to the main tunnel, alternates to the other side, and continues.

### Staff detection

When **AntiAFK** triggers (vanished player detected, sudden TP nearby, or AFK-check DM), AutoMine takes a **random 1–60 second break** — no movement, no mining, no camera movement. Yaw and pitch are locked for the entire break, so from the server's perspective the player has just gone still (as if checking their phone or reading chat). After the break, mining resumes with an elevated miss chance (≥ 55%) for 10 minutes.

**AutoMine never looks around during a staff break** — looking around underground looks exactly like an xray client scanning for ores. The only time AutoMine moves the camera is the brief surprise look-around after mining an unusually large vein (≥ 3 consecutive ores), which mimics a real player reacting to unexpectedly rich ground.

> You need **AntiAFK enabled** for this integration to work. If AntiAFK is off, AutoMine never pauses.

### Settings

| Setting | Default | Effect |
|---|---|---|
| `Ores` | Diamond+Iron | Which ores to target — Diamond+Iron / Diamond / Iron / All Valuable / Everything |
| `BranchEvery` | 16 | Blocks forward between branches |
| `BranchLen` | 8 | Blocks per side branch |
| `OreRadius` | 3 | Radius (blocks) around current position to scan for ores |
| `MissChance` | 15% | Probability the module skips a detected ore (looks human) |
| `PauseChance` | 20% | Probability of a random pause between block breaks |
| `MaxPause` | 30 ticks | Upper bound on random pause length (~1.5 s at default) |
| `UseAI` | off | Sends a one-sentence mining tip to the AI every ~32 forward blocks (requires API key) |

### Tips

- Stand at **Y=−54 to −58** for diamond strip mining (below the diamond peak at Y=−58).
- For ancient debris, try **Y=15** in the Nether.
- The module mines whatever block is in its path — if you start inside a cave, it will clear the cave first before resuming the tunnel pattern.
- Combine with **OreESP** to visually confirm what the bot is collecting.
- The `MissChance` is rolled **once per ore** (when first seen), not every tick — so the same ore is either always collected or always skipped, not flickering.

---

## ServerFinder — Scan for Vulnerable / P2W Servers

**Module:** Misc → `ServerFinder`

Queries the public [mcscans.fi](https://mcscans.fi) server list and filters results two ways, then cross-references the two lists so you can see when a target is both technically exploitable *and* predatory.

### Modes

| Mode | What it scans |
|---|---|
| **Both** (default) | Runs both Vulnerable and P2W scans |
| **Vulnerable** | Only flags servers with exploitable software |
| **P2W** | Only flags servers with pay-to-win / gambling mechanics |
| **AI Search** | Free-text targeted hunt — describe the exploit you want and the AI finds matching servers |

### Vulnerable scan

Matches each server's software type and MOTD against **VulnDb** — ClaudeMC's built-in database of vulnerable plugin versions and unpatched server software. Unpatched Spigot, CraftBukkit, BungeeCord, Waterfall, Mohist, and Magma builds are flagged by software name.

Each result shows: `IP:PORT | version | SEVERITY: Plugin — description`

### P2W / gambling scan

Matches IP and MOTD against a curated list of servers publicly identified on p2w.report and anti-P2W communities as selling gameplay advantages and/or gambling mechanics targeting minors (crate keys, OP spawners for purchase, `/fly` for money, etc.).

With `UseAI` on and an API key configured, the AI is also asked (backed by a DuckDuckGo web search) to produce a broader list of currently-active P2W/gambling servers.

### AI Search mode

Set `Mode` to **AI Search** and type your target exploit into the `Query` setting (right-click the module to expand settings, then left-click the `Query` row to edit it). Examples:

- `EssentialsX dupe`
- `Log4Shell vulnerable`
- `forceop unpatched BungeeCord`

The AI analyses your query, determines which server software is relevant, and queries mcscans.fi for live servers running that software. Results are filtered by the inferred keyword and displayed with player count. Falls back to a VulnDb string-match search if no AI key is configured.

### Cross-reference tags

When both lists are collected, servers that appear in both are tagged:

| Tag | Meaning |
|---|---|
| `[⚠ ALSO P2W]` | Shown on vulnerable servers that are also P2W — exploitable *and* predatory |
| `[⚠ EXPLOITABLE]` | Shown on P2W servers that also run vulnerable software |

Cross-tags appear regardless of which `Mode` you are viewing, because both lists are always collected internally.

### Settings

| Setting | Default | Effect |
|---|---|---|
| `Mode` | Both | Both / Vulnerable / P2W / AI Search |
| `UseAI` | on | Use AI + web search for P2W context and AI Search mode |
| `OfflineOnly` | off | Restrict results to offline-mode (cracked) servers only |
| `MaxResults` | 20 | Max servers shown per category (5–100) |
| `Query` | *(empty)* | Free-text exploit query used in AI Search mode — left-click to edit in-game |

The module is trigger-only — enable it once to fire a scan, then it disables itself. Results appear in local chat only; no data is sent anywhere except mcscans.fi.

---

## Cracked Minecraft (TLauncher etc.)

**Short answer: yes.** ClaudeMC is a standard Fabric client mod — it works on any Minecraft installation regardless of how the game was launched, including TLauncher, MultiMC in offline mode, PolyMC, ATLauncher, and any other launcher.

### What you need

| What | Notes |
|---|---|
| Minecraft Java Edition 1.21.1 | Any launcher that can run this version |
| Fabric Loader ≥ 0.16.5 | Install via the launcher's built-in profile creator or the Fabric Installer |
| Fabric API 0.107.0+1.21.1 | Downloadable from [modrinth.com/mod/fabric-api](https://modrinth.com/mod/fabric-api) |

### TLauncher — step by step

1. **Download and install TLauncher** from [tlauncher.org](https://tlauncher.org) if you don't have it.
2. In the version selector, type `1.21.1` and look for **Fabric 1.21.1** in the list (TLauncher bundles Fabric profiles). Select it and click **Install** / **Play** once to let it download.
   - If Fabric 1.21.1 doesn't appear: download the Fabric Installer from [fabricmc.net/use](https://fabricmc.net/use/) and run it pointing at your TLauncher game directory.
3. **Find the mods folder.** Default locations:
   - Windows: `%AppData%\.minecraft\mods\`
   - macOS: `~/Library/Application Support/minecraft/mods/`
   - Linux: `~/.minecraft/mods/`
   - TLauncher uses the same `.minecraft` folder as the vanilla launcher by default. If you set a custom game directory in TLauncher, use that path instead.
4. **Drop in the JARs:**
   - `fabric-api-0.107.0+1.21.1.jar` (or equivalent version)
   - `claudemc-1.19.1.jar` (from the Releases page)
5. Launch the **Fabric 1.21.1** profile in TLauncher.
6. You should see `ClaudeMC v2 initialised` in the log, and the `.` key opens the ClickGUI in-game.

### Online-mode vs offline-mode servers

| Server type | Works? | Notes |
|---|---|---|
| **Offline-mode / cracked servers** | ✅ | Any username works. No Microsoft account needed. |
| **Online-mode servers** | ✅ with valid account | Requires a genuine Microsoft session. TLauncher Premium (paid) or a real account entered via TLauncher works. Without a valid session the server will reject the connection with "Not authenticated with Minecraft.net" — that is a server restriction, not a mod restriction. |

### Alt Manager

ClaudeMC includes a built-in **Alt Manager** (press `.` → `[Alts]` in the footer). You can add **Offline alts** (just a username — works on cracked servers) or **Session alts** (username + UUID + access token — for online-mode servers).

---

## Editing Module Settings

As of **v1.18** every module setting — including free-text fields — is editable live in the ClickGUI. No file editing, no rebuilding required.

1. Press **`.`** to open the ClickGUI.
2. **Right-click** a module to expand its settings.
3. Interact with the setting row:

| Setting type | Left-click | Right-click |
|---|---|---|
| **Number** (e.g. Range, Speed) | Increase by one step | Decrease by one step |
| **Toggle** (e.g. Rotate, ShowFull) | Flip on/off | Flip on/off |
| **Option** (e.g. Mode, Filter) | Next option | Previous option |
| **Text** (e.g. Query, Password) | Enter edit mode — cursor appears, type freely, **Enter** or click away to save, **Esc** to cancel | Clear the field |

Settings are written to `config/claudemc/modules.json` the moment you change them and restored on the next launch.

> Module **enabled/disabled** state is intentionally *not* restored on startup — only setting values are — so nothing activates before you join a world.

---

## Trajectories — Projectile Prediction

**Module:** Render → `Trajectories`

Draws the predicted flight path of shootable/throwable items, integrated with the same gravity/drag model the vanilla projectiles use and clipped against the world so the line ends where the projectile would actually land (marked with a small box).

| Setting | Default | Effect |
|---|---|---|
| **Self** | on | Draw the arc for the item you are holding / drawing (**cyan**) |
| **Others** | on | Draw arcs for nearby entities that are actively aiming (**orange-red**) |

**Supported items**

| Item | When the arc shows | Notes |
|---|---|---|
| **Bow** | While drawing | Arc speed scales with your draw progress (a barely-drawn bow shows nothing) |
| **Crossbow** | While loaded | Full-power bolt arc |
| **Trident** | While held / charging | Riptide throws are not predicted |
| **Snowball / Egg / Ender Pearl** | While held (self) | Standard throw arc |
| **Splash / Lingering Potion** | While held (self) | Includes the vanilla −20° lob |
| **Experience Bottle** | While held (self) | Lobbed arc |

**Enemy trajectories:** with **Others** enabled, any player or mob within ~64 blocks that is *actively drawing a bow / charging a trident* or *holding a loaded crossbow* gets a red arc, so you can see incoming shots and where they'll land. (Held throwables are only drawn for yourself, to avoid clutter.)

---

## BlockESP — Custom Blocks

By default BlockESP highlights all 16 shulker box colours, chests (incl. trapped/ender), barrels, spawners, and ancient debris.

**Add or remove blocks with no coding (v1.9+):**

- **Crosshair keybind (default `B`):** look at any block in the world and press **B** to add it to the tracking list instantly — press again while looking at it to remove it. Rebind the key in the **[Keybinds]** screen ("BlockESP: Add block" row).
- **Block manager GUI:** press **`.`** → **[BlockESP]** in the footer. The left pane lists your tracked blocks (click to remove); the right pane is a searchable list of every block in the game (click to add/remove, a green ✔ marks tracked ones).

Your additions and any removed defaults are saved to `config/claudemc/blockesp.json` and restored on the next launch.

---

## RecordProof — Screen Capture Hiding

**Platform:** Windows 10 version 2004 (May 2020 Update) or newer, Windows 11.

When enabled, ClaudeMC calls `SetWindowDisplayAffinity(HWND, WDA_EXCLUDEFROMCAPTURE)` through a
short PowerShell `Add-Type` bridge (the window handle is obtained from GLFW) — no extra native
libraries or JNA needed.

**What it hides from:**
- Discord screen share (window capture mode)
- OBS Studio — Window Capture source
- NVIDIA GeForce Experience / ShadowPlay (GameBar)
- Xbox Game Bar recording

**What it does NOT hide from:**
- OBS **Game Capture** (captures the GPU frame buffer directly — OS-level blocks can't stop it)
- Physical screen viewing / cameras pointed at your monitor

**Steps:**
1. Launch Minecraft normally.
2. Open ClickGUI (`[.]`) → Misc → enable **RecordProof**.
3. The window disappears from any active screen shares instantly.
4. Disable the module to restore normal visibility.

> If you see `[RecordProof] Not supported on this platform`, you are on macOS or Linux where this API is unavailable.

---

## Dupe Shortcuts (dupedb.net)

These exploits replicate glitches documented on [dupedb.net](https://dupedb.net).

### BookDupe

**Works on:** Vanilla / Spigot servers without Paper's packet-rate fix.  
**How it works:** Sends 5 consecutive `BookUpdateC2SPacket`s in one tick before the server
processes them, yielding a duplicate book for each extra packet.

**Steps:**
1. Hold a **Book and Quill** (written book also works) in your main hand.
2. Enable **BookDupe** in Misc.
3. The dupe fires once on enable. Re-toggle to repeat.
4. Check your inventory — you should have extra books.

**Patched on:** Paper 1.19.3+, Purpur, modern AntiCheat plugins.

### DisconnectDupe (manual)

**Works on:** Most servers without rollback protection.

1. Place valuable items in a chest.
2. Take the items out of the chest (they are now in your inventory).
3. **Immediately** disconnect from the server (before the server saves).
4. Some servers roll back your inventory but not the chest — you keep both.

> Not automated because reliable detection of the correct disconnect timing is server-specific.

### AuctionDupe

**Works on:** Servers whose auction-house plugin has race-condition bugs (common on older Spigot AH plugins, unpatched EssentialsX market, early AuctionHouse by Kicjow).  
**Three techniques** — select in settings:

| Technique | How it works |
|---|---|
| **WindowClose** | Sends rapid `CloseHandledScreenC2SPacket`s while the cancel-confirm GUI is open. Race condition returns item before cancel is finalised — you keep the coins *and* receive the item back. |
| **DoubleCancel** | Sends N rapid slot-click packets for the confirm button in the same tick. Thread-unsafe plugins process multiple cancels, returning the item multiple times. |
| **Reconnect** | Closes the screen (triggering server-side item return), then disconnects immediately before the server persists the transaction. On reconnect: item is in your inventory, listing is gone / coins also returned. |

**Steps:**
1. Open the auction house and navigate to your listed item's **Cancel / Retrieve** screen.
2. Enable **AuctionDupe** in the Misc panel (`.` → Misc → AuctionDupe).
3. The exploit fires automatically after a short delay (default 3 ticks) then self-disables.
4. Check inventory / balance for the duplicate.

> Set **Technique** in the module settings (right-click AuctionDupe in the GUI). Default is `WindowClose`.  
> Increase **Packets** (default 5) if the race window is narrow.

**Patched on:** AuctionHouse 1.3.6+, paper-patched GUIs, any plugin with synchronous atomic item commits.

### TNT Dupe (setup guide)

Not automated due to world-edit requirements. See [dupedb.net/tnt](https://dupedb.net) for the piston layout. The **Timer** module (set to 0.5×) can slow the game to help time the activation.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| "Module list is empty" | Ensure Fabric API jar is in `mods/` |
| ESP boxes flicker | Disable other shader mods (OptiFabric, Iris) |
| Flight resets in survival | Some servers with AntiCheat reset abilities each tick — use Packet mode |
| RecordProof has no effect | Windows only; check you are on 1803+ |
| BookDupe gives no extra books | Server is patched (Paper/Purpur) |
| TPS shows 0.0 | Not connected to a server |
| Build fails with "Plugin not found" | You need internet access to `maven.fabricmc.net` |
| `java.lang.foreign` errors | You must run Java 21 (not Java 17) |

---

---

## ForceOP

**Works on:** Servers with misconfigured permissions, old Spigot builds without validation, outdated BungeeCord proxies.  
**Patched on:** Paper 1.19+, Purpur, modern Spigot with a current build.

Three techniques are tried simultaneously:

| Technique | How |
|---|---|
| **Command** | Sends `/op <name>` as a chat command — instant if you already have permissions |
| **CommandBlock packet** | Sends `UpdateCommandBlockC2SPacket` to pos 0,0,0 with `/op <name>` — exploits servers that don't validate the sender has OP before processing command-block updates |
| **BungeeCord plugin-message** | Sends a `ConnectOther` plugin-message on the `BungeeCord` channel — old Bungee proxies allow any client to issue this if the hub server has console-forwarding plugins installed |

The module **auto-disables after one attempt**. Watch chat for the server's response. If you see `Unknown command` or `You do not have permission`, the server is patched.

---

## ForceCreative

Three techniques, all run simultaneously:

| Technique | What it does | Works when |
|---|---|---|
| **Command** | `/gamemode creative` | You have OP or a permissions plugin grants it |
| **Abilities spoof** | Sets `creativeMode=true`, `allowFlying=true`, `invulnerable=true` in the abilities packet, re-sent every second | Server doesn't validate game mode before accepting ability flags |
| **Client spoof** | Overrides the local `currentGameMode` field to `CREATIVE` via reflection | Always — gives you creative block-break speed / reach client-side regardless of server |

**What you get on a vulnerable server:** flight, no fall damage, no hunger, instant block break, infinite items from the creative inventory.  
**What you get on a patched server:** client-side spoof only (local break speed / reach) — the server will reset your actual game mode.

---

## AuthMeBypass — Proxy Auth Escape

**Module:** Misc → `AuthMeBypass`

Exploits the command-processing order in BungeeCord/Velocity proxies to skip AuthMe authentication entirely on cracked servers.

### How it works

BungeeCord (and Velocity) processes the `/server` command at the **proxy layer**, before the packet ever reaches the backend Spigot/Paper server where AuthMe is installed. During the AuthMe login phase the proxy still accepts `/server`, so sending it transfers you to a backend sub-server that has no authentication requirement — you arrive authenticated as whatever username you connected with.

```
Client  ──/server hub──▶  BungeeCord  ──routes──▶  backend "hub" server
                         (no AuthMe here)           (no auth required)
```

### Steps

1. **Set your username** to the target player's name in the Alt Manager (press `.` → `[Alts]` → `[+ Offline]`).
2. **Connect** to the cracked server normally. BungeeCord routes you to the lobby/auth server.
3. **Enable `AuthMeBypass`** before AuthMe completes its setup, or leave `AutoTrigger` on so it fires automatically the moment a `/login` or `/register` prompt appears.
4. The module discovers available sub-servers by sending a tab-complete request (`/server<TAB>`) to the proxy, then tries them in sequence with `/server <name>`.
5. On successful transfer you receive `Bypass successful — on backend server` in local chat.

### Settings

| Setting | Default | Effect |
|---|---|---|
| `AutoTrigger` | on | Fire automatically when an AuthMe login/register prompt is detected |
| `DelayTicks` | 10 | Ticks to wait after detecting the prompt before probing (lets the prompt fully render) |
| `RetryTicks` | 30 | Ticks between `/server` attempts when trying multiple names |

### Works against

- BungeeCord / Waterfall + AuthMe (all versions) in offline mode
- Velocity proxies (Velocity also processes `/server` before backend forwarding)

### Does NOT work against

- **BungeeGuard** — adds a forwarding secret that backends verify; direct sub-server connections are rejected
- **IP whitelist on backends** — backends only accept connections from the proxy IP, not directly
- **Online-mode servers** — requires a valid Microsoft session; offline bypass is irrelevant
- Servers where the proxy restricts `/server` to specific game-modes only (e.g. only `hub`)

> **Note:** If you have a list of backend server names (e.g. from a friend or `/server` error messages), the module will try those first via the tab-complete response. Otherwise it falls back to 20 common sub-server names automatically.

---

## VanishDetect — Packet Leak Tracking

Improved in v1.3 to use two detection layers:

### Layer 1 — Tab-list cross-reference (always works)
Every tick we compare the tab-list UUIDs against UUIDs of actual world entities. Any player in the tab list with no world entity is shown with a **magenta ESP box** at their last seen position.

### Layer 2 — EntityPosition packet leak (works on simple vanish plugins)
Most vanish plugins (e.g. vanilla `vanish`, EssX old builds, simple home-brew plugins) work by suppressing the **SpawnEntity** packet for non-OP players. However they often **still forward**:
- `EntityPositionS2CPacket` — absolute teleport updates
- `EntityS2CPacket.MoveRelative` / `RotateAndMoveRelative` — walking movement deltas

`VanishTrackingMixin` intercepts all four packet types. When we receive a position/move packet for an entity ID that was previously removed (via `RemoveEntitiesS2CPacket`) but whose UUID is still in the tab list, we update the ghost position map. The ESP box **follows the vanished player in real-time** as they walk around.

Premium vanish plugins (PremiumVanish, advanced EssX) suppress these movement packets correctly, so only Layer 1 applies there.

---

## Mod Compatibility

### Compatible (safe to use alongside ClaudeMC)

| Mod | Notes |
|---|---|
| **Sodium** | Fully compatible — rendering performance improvement, no conflicts |
| **Lithium** | Fully compatible — server-side logic optimisation for singleplayer |
| **FerriteCore** | Fully compatible — memory usage reduction |
| **ModMenu** | Fully compatible — shows ClaudeMC in the mod list |
| **Replay Mod** | Compatible, but RecordProof will also hide the window from ReplayMod capture |
| **MiniHUD** | Compatible — HUD elements may overlap; reposition ClaudeMC panels if needed |
| **Tweakeroo** | Mostly compatible; some movement tweaks may conflict with Flight/Speed modules |

### Incompatible / Conflicts

| Mod | Why |
|---|---|
| **OptiFabric / OptiFine** | Breaks Mixin injection — do not use; use Sodium instead |
| **Iris Shaders** | ESP boxes may flicker or disappear — the shader pipeline overrides the render layer |
| **Indium** | Required if using Sodium + Iris; no additional conflicts with ClaudeMC itself |
| **Meteor Client** | Cannot run alongside ClaudeMC — both register the same Mixin targets and keybinds |
| **Wurst Client** | Same conflict as Meteor — only one hack client at a time |
| **LabyMod** | Replaces core GUI rendering; ClickGUI panels may not render correctly |
| **Essential Mod** | Conflicts with session/alt management — do not use AltManager alongside Essential |

### Shader note

If you use Iris + Sodium and want shaders, ESP boxes will not render through walls. The rest of ClaudeMC functions normally. To use ESP with shaders, disable the shader pack while ESP is active.

---

## Chat Overlay (UIUtils)

Press **`T`** while any GUI is open (auction house, chest, crafting table, etc.) to open the floating chat input box without closing the current screen. Press **Enter** to send, **Esc** to dismiss.

- Supports full text editing (backspace, delete, left/right arrow, home/end)
- Prepend `/` to send a command instead of a chat message
- Works in any screen — you never have to close the GUI to type

---

## Macros

Open **`.`** → click **`[Macros]`** in the footer.

- **Add:** click `[+ New Macro]`, type a name and command (e.g. `/tp spawn`), press Tab to cycle fields, Enter to save
- **Keybind:** tab to the Key field; it enters listening mode automatically — press any key to bind
- **Delete:** right-click any macro row
- **Fire:** press the bound key in-game (while no screen is open), or run via the Chat Overlay

---

## Alt Manager

Open **`.`** → click **`[Alts]`** in the footer.

**Offline / Cracked alts** — works on offline-mode and cracked servers:
1. Click `[+ Offline]`
2. Enter a username → Enter

**Session alts** (online-mode servers) — requires a pre-obtained access token:
1. Click `[+ Session]`
2. Enter username, UUID, and the Microsoft access token → Enter
3. Tokens can be obtained from external auth tools (not included)

Click any row to switch to that account. Click **`[Restore]`** to switch back to your original account. Changes take effect on the next server connection — you must reconnect after switching.

> **Warning:** switching alts while already connected to a server will not work mid-session. Always switch before joining.

---

## MiniMessage Exploit

**Module:** Misc → `MiniMessageExploit`

Based on the [khaodoes.dev MiniMessage escape exploit](https://khaodoes.dev/blog/minimessage-escape-exploit) and extended with payloads from [dupedb.net](https://dupedb.net). Targets plugins (EssentialsX < 2.21.0, ZelChat, SimpleTeams 2.0.0, TAB, DiscordSRV, custom chat plugins) that pass player chat or team data through MiniMessage without sanitising tags.

Enable once → fires the selected payload → auto-disables.

| Technique | Target plugin / condition | What it does |
|---|---|---|
| **ClickCommand** | No sanitiser | `<click:run_command:…>` — any player who clicks the message executes the injected command |
| **HoverSpoof** | No sanitiser | Fakes a `[SERVER]` broadcast — visual deception / phishing |
| **GradientBypass** | Plugins filtering plain strings | Wraps text in `<gradient>` — bypasses word-match filters |
| **EscapeInject** | Single-pass strippers | `\<` escape sequences survive sanitisers that only strip unescaped `< >` tags |
| **FontObfuscate** | Font-sensitive filters | Renders in `<font:uniform>` — different visual, bypasses font-keyed word filters |
| **ZelChat** | ZelChat (pre-patch) | `<<aqua>aqua ><click:run_command:…>` — nesting the opening bracket fools ZelChat's tag stripper *(Khao/Linux)* |
| **ZelChatV2** | ZelChat after first patch | Double-nested bracket defeats ZelChat's second stripping pass *(Khao/Bright6f)* |
| **SimpleTeams** | SimpleTeams 2.0.0 | Creates/updates a team and injects the click payload into the prefix; fires for any player who runs `/team info <name>` |
| **TotemHand** | Servers with `[item]`/`[hand]` chat placeholders | Sends `[item]` in chat; the item name payload executes when displayed. Rename a Totem via anvil with the MiniMessage payload first |

Settings:

| Setting | Default | Effect |
|---|---|---|
| `Technique` | ClickCommand | Which payload to fire — cycle with left/right click |
| `Target` | *(your name)* | Username substituted into `{target}` in the command |
| `CustomText` | Click to claim your free rank! | Visible text shown to other players |
| `CustomCmd` | `/op {target}` | Command injected into click payloads |
| `TeamName` | claudemc | Team name created/used by the SimpleTeams technique |

### SimpleTeams prefix injection

SimpleTeams 2.0.0 applies no tag sanitisation to the `/team edit prefix` command. The module:

1. Sends `/team create <TeamName>` (silently ignores if the team already exists)
2. Waits 1 second, then sends `/team edit prefix <TeamName> <click:run_command:'…'>…</click>`
3. Notifies you in local chat to direct victims to `/team info <TeamName>`

When a victim runs `/team info`, the prefixed click event appears in their chat. One click executes the injected command as them.

### TotemHand (item name relay)

1. Open an **Anvil**.
2. Place a Totem of Undying in the first slot.
3. In the name field, type the MiniMessage payload: `<click:run_command:'/op YourName'>[Click here]`
4. Take the renamed totem and hold it in your main hand.
5. Enable `MiniMessageExploit` with `Technique = TotemHand`. The module sends `[item]` in chat.

Many servers broadcast the item name verbatim. If the chat plugin passes item names through MiniMessage without stripping, the click event executes for anyone who clicks it.

---

## Server Crash

**Module:** Misc → `ServerCrash`

Sends crash-inducing packets targeting unpatched Spigot/CraftBukkit servers. Patched on Paper 1.19.3+.

| Technique | Target |
|---|---|
| **BookOverflow** | 100 pages × 32767 chars — crashes servers processing book NBT synchronously on the main thread |
| **PacketFlood** | Rapid `CloseHandledScreenC2SPacket` spam — overflows the packet queue on servers without rate limiting |
| **NBTOverflow** | 512-level deep nested NBT compound — crashes servers without NBT depth limits |
| **SignOverflow** | Sign update with 32767-char lines — crashes old sign-handling code |

Enable once → fires immediately → auto-disables.

---

## Credits

- Module system inspired by [Meteor Client](https://meteorclient.com/)
- Dupe research from [dupedb.net](https://dupedb.net)
- MiniMessage exploit research: [khaodoes.dev](https://khaodoes.dev/blog/minimessage-escape-exploit)
- Built with [Fabric API](https://fabricmc.net) and [LWJGL 3](https://www.lwjgl.org/)
