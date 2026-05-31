# ClaudeMC — Architecture

A high-level map of how the mod is put together, for anyone modifying it. Target platform is
Minecraft **1.21.1** + Fabric Loader, Yarn mappings, Java 21.

## Entry points

| Class | Role |
|---|---|
| `ClaudeMCMod` | `main` entrypoint — holds the shared `LOGGER`. |
| `ClaudeMCClient` | `client` entrypoint — constructs the managers, loads config, registers the tick handler and HUD, owns the keybind dispatch loop. |

`fabric.mod.json` wires both, plus the mixin config `claudemc.mixins.json`. `${version}` is
expanded at build time from `mod_version` in `gradle.properties`.

## Module system

```
Module (abstract)
 ├─ name / description / Category / enabled
 ├─ List<Setting> settings
 ├─ onEnable() / onDisable() / onTick(client)
 └─ toggle() / setEnabled()
ModuleManager  – registers every module, fans out onTick() to enabled ones, getByCategory(), get(Class)
```

- A module is registered once in `ModuleManager`'s constructor.
- `ModuleManager.onTick()` is called every client tick from `ClaudeMCClient` and only ticks
  **enabled** modules.
- Render-only modules (ESP-style) register a `WorldRenderEvents.AFTER_ENTITIES` callback in their
  constructor and gate it on `isEnabled()`.

### Settings (`com.claudemc.module.setting`)

Typed, GUI-editable settings. Values are still readable as strings (`Module.getSetting(name)`),
so module logic parses them exactly as before, while the GUI and persistence operate on the typed
object.

| Type | Use |
|---|---|
| `BoolSetting` | on/off toggle |
| `NumberSetting` | bounded int or double with a step (clamped) |
| `ModeSetting` | cycle through a fixed option list |
| `StringSetting` | opaque free text (not cycle-editable) |

`Module.addSetting(name, default)` **infers** the type from the default string ("true"/"false" →
bool, integer → int, decimal → double, else string), so legacy registrations became editable for
free. New code should prefer the explicit helpers `addBool` / `addNumber` / `addMode` when bounds
or option lists matter. `ModuleConfig` persists every setting's `asString()` to
`config/claudemc/modules.json` and restores it on init (enabled-state is **not** restored).

### Shared scan engine

`BlockScanModule` is the base for radius ESP modules (`BlockESP`, `StorageESP`). It runs the
expensive world sweep on a **throttled client tick** (default every 8 ticks), publishes an
immutable `List<Highlight>` snapshot, and the render callback only draws that snapshot — keeping
heavy `getBlockState`/`getBlockEntity` work off the render path. Subclasses implement
`scan(client, out)`.

## Rendering

`RenderUtils` wraps the vertex-consumer plumbing: `drawOutlinedBox`, `drawLine`, and
`drawLineStrip` (single-flush poly-line used by `Trajectories`). All geometry is passed in
**camera-relative** space (subtract `context.camera().getPos()`).

## HUD

`HudManager` (registered from `ClaudeMCClient`) renders the watermark, module list, coords, stats
(FPS/TPS/ping) and armour. TPS is estimated in `HudManager.onWorldTimeUpdate()`, called from a
mixin on the world-time packet. There is one source of truth — read `HudManager.getEstimatedTps()`.

## Threading model

Three relevant threads; respect which one touches shared state:

| Thread | What runs there |
|---|---|
| **Network (netty)** | Packet decode; some `ClientPlayNetworkHandler` injections. Writes to `ServerInfo` and `HudManager`'s TPS counter originate from packet handling. |
| **Client/main** | `onTick`, screen input, module logic. |
| **Render** | `WorldRenderEvents` / HUD callbacks. |

`ServerInfo` is read from render/GUI and written from packet handling, so its channel set is
synchronized. `BlockScanModule` publishes its snapshot via a `volatile` field. Network/IO
(`ExploitFetcher`) runs on a `CompletableFuture` and exposes results through `volatile` fields.

## Mixins (`com.claudemc.mixin`)

| Mixin | Target → purpose |
|---|---|
| `MinecraftClientMixin` | `render` — Timer module tick scaling |
| `ClientPlayerEntityMixin` | `sendMovementPackets` — NoFall on-ground spoof |
| `ClientPlayNetworkHandlerMixin` | world-time/brand/channels/disconnect taps |
| `VanishTrackingMixin` | entity destroy/move packets for VanishDetect (reflective field reads, warns once on failure) |
| `ScreenMixin` | route key/char input to the chat overlay |
| `EntityMixin` / `GameRendererMixin` | misc render/entity hooks |

Injections that target optional/renamable methods use `require = 0` so a mapping change disables
the hook instead of crashing the game.

## Persistence (under `config/claudemc/`)

| File | Owner |
|---|---|
| `keybinds.json` | `KeybindManager` |
| `macros.json` | `MacroManager` |
| `modules.json` | `ModuleConfig` (setting values) |
| `blockesp.json` | `BlockESP` (custom + removed-default block ids) |
| `exploits_cache.json` | `ExploitFetcher` (remote DB cache, 6h TTL) |

## CI

| Workflow | Trigger | Does |
|---|---|---|
| `build.yml` | push (non-VERSION) / PR | `./gradlew build` (compile + unit tests) — no publishing |
| `release.yml` | push that changes `VERSION` | build + tag `v<VERSION>` + GitHub Release with the JAR and notes from `.github/release-notes/v<VERSION>.md` |
| `backfill-jar.yml` | manual | attach a built JAR to an existing release tag |

Unit tests live in `src/test/java` and cover only pure-logic classes (no Minecraft runtime):
the setting types and `ServerInfo`.
