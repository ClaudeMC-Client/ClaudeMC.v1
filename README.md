# ClaudeMC v1.10.0

A **Meteor Client-style** Fabric mod for Minecraft **1.21.1** featuring a full in-game overlay, ClickGUI, ESP through walls, projectile trajectory prediction, survival flight, combat assists, dupe exploits, and more.

> **What's new in v1.10:** projectile **Trajectories** module (see your own *and* enemy arrow/throwable arcs), **editable settings** directly in the ClickGUI (click a setting to cycle/adjust — values now persist across restarts), plus an internal refactor (typed settings, shared scan engine) and unit tests. See [v1.9.1] for the preceding performance/bugfix pass.

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
8. [Editing Module Settings](#editing-module-settings)
9. [Trajectories — Projectile Prediction](#trajectories--projectile-prediction)
10. [BlockESP — Custom Blocks](#blockesp--custom-blocks)
11. [RecordProof — Screen Capture Hiding](#recordproof--screen-capture-hiding)
12. [Dupe Shortcuts (dupedb.net)](#dupe-shortcuts-dupedbnets)
13. [Troubleshooting](#troubleshooting)

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
build/libs/claudemc-1.10.0.jar
```
(There will also be a `claudemc-1.10.0-sources.jar` — ignore that one.)

**5. Install it**

Copy the JAR to your mods folder:

```bash
# Windows
copy build\libs\claudemc-1.10.0.jar %APPDATA%\.minecraft\mods\

# macOS
cp build/libs/claudemc-1.10.0.jar ~/Library/Application\ Support/minecraft/mods/

# Linux
cp build/libs/claudemc-1.10.0.jar ~/.minecraft/mods/
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

> **Settings are editable directly in the GUI (v1.10+).** Expand a module (right-click it), then
> **left-click a setting to increase / toggle / pick the next option, right-click to go back.**
> Numbers are bounded and step sensibly; on/off toggles flip; multiple-choice settings cycle.
> All values are saved to `config/claudemc/modules.json` and restored on the next launch.
> (A handful of free-text settings — e.g. exploit command strings — show in grey and are not
> click-editable.) See [Editing Module Settings](#editing-module-settings) for details.

---

## Module Reference

### Combat Modules

| Module | Description | Key Settings |
|---|---|---|
| **KillAura** | Auto-attacks nearby entities each tick | Range (blocks), Target (Hostile+Players/Players/Hostile/All), Rotate |
| **Velocity** | Reduces knockback received when hit | H-Mult (0=none), V-Mult (1=normal) |
| **AutoTotem** | Moves Totem of Undying to offhand automatically | — |
| **Criticals** | Makes every swing a critical hit (tiny hop) | Mode (Jump/Packet) |

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

### Player Modules

| Module | Description | Key Settings |
|---|---|---|
| **AutoEat** | Eats food when hunger drops below threshold | Threshold (0–20) |
| **FastBreak** | Removes mining animation delay | — |
| **ChestStealer** | Shift-clicks all items from open chests | Delay (ticks) |
| **AntiHunger** | Prevents sprint-exhaustion hunger drain | — |
| **AutoArmor** | Equips the best armour from your inventory | — |

### Render / ESP Modules

| Module | Description | Key Settings |
|---|---|---|
| **ESP** | Coloured entity outlines through walls | Filter (All/Players/Hostile) |
| **BlockESP** | Highlights shulkers, chests, spawners + your custom blocks | Radius (managed via the [BlockESP] GUI / **B** key) |
| **StorageESP** | Shows container fill level through walls | Radius, ShowFull, ShowEmpty |
| **Tracers** | Lines from screen centre to entities | Filter, Range |
| **Trajectories** | Predicts arrow/throwable flight paths (yours + enemies') | Self, Others |
| **Fullbright** | Maximum light everywhere (no torch needed) | — |
| **FreeCam** | Detach camera from body | Speed |
| **Nametags** | Shows health/ping/distance above player heads | Health, Ping, Dist |
| **AntiInvis** | Renders invisible and vanished players | Opacity |

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

---

## Editing Module Settings

As of **v1.10** module settings are edited live in the ClickGUI — no rebuilding required.

1. Press **`.`** to open the ClickGUI.
2. **Right-click** a module to expand its settings.
3. Click a setting value:
   - **Numbers** (e.g. `Range`, `Radius`, `Speed`) — **left-click increases**, **right-click decreases**, clamped to a sensible range and step.
   - **Toggles** (e.g. `Rotate`, `ShowFull`) — either click flips on/off.
   - **Options** (e.g. ESP `Filter` = All/Players/Hostile) — **left-click = next**, **right-click = previous**.

Settings are written to `config/claudemc/modules.json` the moment you change them and restored on the next launch. Free-text settings (a few exploit command strings) are shown in grey and are not click-editable; edit `modules.json` directly if you need to change those.

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

## ForceOP (v1.3)

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

## ForceCreative (v1.3)

Three techniques, all run simultaneously:

| Technique | What it does | Works when |
|---|---|---|
| **Command** | `/gamemode creative` | You have OP or a permissions plugin grants it |
| **Abilities spoof** | Sets `creativeMode=true`, `allowFlying=true`, `invulnerable=true` in the abilities packet, re-sent every second | Server doesn't validate game mode before accepting ability flags |
| **Client spoof** | Overrides the local `currentGameMode` field to `CREATIVE` via reflection | Always — gives you creative block-break speed / reach client-side regardless of server |

**What you get on a vulnerable server:** flight, no fall damage, no hunger, instant block break, infinite items from the creative inventory.  
**What you get on a patched server:** client-side spoof only (local break speed / reach) — the server will reset your actual game mode.

---

## VanishDetect — Packet Leak Tracking (v1.3)

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

Based on the [khaodoes.dev MiniMessage escape exploit](https://khaodoes.dev/blog/minimessage-escape-exploit). Targets plugins (EssentialsX < 2.21.0, TAB, custom chat plugins) that pass player chat through MiniMessage without sanitising tags.

Enable once → fires the selected payload → auto-disables.

| Technique | What it does |
|---|---|
| **ClickCommand** | Wraps your text in `<click:run_command:'/cmd'>` — any player who clicks the message in chat executes the injected command |
| **HoverSpoof** | Fakes a `[SERVER]` broadcast using `<red><bold>` + `<hover>` — visual deception |
| **GradientBypass** | Wraps text in `<gradient>` tags — bypasses simple chat filters that match plain strings |
| **EscapeInject** | Uses `\<` escape sequences to survive sanitisers that only strip unescaped tags |
| **FontObfuscate** | Renders text in `<font:uniform>` — different visual appearance, bypasses font-sensitive filters |

Settings: **Target** (username for ClickCommand, default = yourself), **CustomText** (visible text), **CustomCmd** (injected command, `{target}` is replaced).

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
