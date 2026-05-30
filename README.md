# ClaudeMC v1.3

A **Meteor Client-style** Fabric mod for Minecraft **1.21.1** featuring a full in-game overlay, ClickGUI, ESP through walls, survival flight, combat assists, dupe exploits, and more.

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
8. [BlockESP — Custom Blocks](#blockesp--custom-blocks)
9. [RecordProof — Screen Capture Hiding](#recordproof--screen-capture-hiding)
10. [Dupe Shortcuts (dupedb.net)](#dupe-shortcuts-dupedbnets)
11. [Troubleshooting](#troubleshooting)

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

1. Download `claudemc-2.0.0.jar` from the releases page (or build from source).
2. Place it into `.minecraft/mods/`.
3. Launch Minecraft with the **Fabric 1.21.1** profile.
4. You should see `ClaudeMC v2 initialised` in the log.

---

## Building from Source

```bash
# Prerequisites: JDK 21+, internet access (for maven.fabricmc.net)

git clone https://github.com/l0azathkamil/claudemc.v1.git
cd claudemc.v1

# Windows
gradlew.bat build

# macOS / Linux
./gradlew build

# Output: build/libs/claudemc-2.0.0.jar
```

Copy the output jar into your `mods/` folder.

---

## In-Game Controls

| Key | Action |
|---|---|
| **`.`** (full stop) | Open / close the ClickGUI |
| **Esc** | Close the ClickGUI |
| **Left-click module** | Toggle module on / off |
| **Right-click module** | Expand / collapse module settings |
| **Drag panel header** | Move that category panel |

> Tip: Panels remember their positions across sessions (stored per-run).

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
│   Range: 4.0             │  ← editable in future version
│   Target: Hostile+Players│
│   Rotate: true           │
```

> Settings editing via text input is planned for v2.1. For now, edit the default values in the module source files and rebuild.

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
| **BlockESP** | Highlights shulkers, chests, spawners through walls | Radius, Shulkers, Chests, Spawners, Custom IDs |
| **StorageESP** | Shows container fill level through walls | Radius, ShowFull, ShowEmpty |
| **Tracers** | Lines from screen centre to entities | Filter, Range |
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
| **VanishDetect** | Marks players in tab list with no world entity |
| **RecordProof** | Hides window from Discord/OBS (Windows only) |
| **NoPacketKick** | Suppresses invalid-packet kick attempts |
| **ForceOP** | Fires multiple OP-grant techniques on vulnerable servers |
| **ForceCreative** | Spoofs abilities packet + client game-mode for Creative |

---

## BlockESP — Custom Blocks

By default BlockESP highlights:
- All 16 shulker box colours
- Chest, Trapped Chest, Ender Chest, Barrel
- Spawner
- Ancient Debris

To **add a custom block** at runtime (via chat command or a future keybind):

```
# In the mod source, call:
BlockESP.INSTANCE.addTarget("minecraft:diamond_ore");
BlockESP.INSTANCE.addTarget("minecraft:deepslate_diamond_ore");
```

A full runtime command interface is planned for v2.1.

---

## RecordProof — Screen Capture Hiding

**Platform:** Windows 10 version 2004 (May 2020 Update) or newer, Windows 11.

When enabled, ClaudeMC calls `SetWindowDisplayAffinity(HWND, WDA_EXCLUDEFROMCAPTURE)` via the
Java 21 Foreign Function & Memory API — no extra native libraries or JNA needed.

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

## Credits

- Module system inspired by [Meteor Client](https://meteorclient.com/)
- Dupe research from [dupedb.net](https://dupedb.net)
- Built with [Fabric API](https://fabricmc.net) and [LWJGL 3](https://www.lwjgl.org/)
