# Contributing to ClaudeMC

## Prerequisites

- **JDK 21** (full kit). Java 17/22 will not work.
- Git. You do **not** need a separate Gradle install — use the bundled `./gradlew`.

Development happens on the branch `claude/serene-volta-eY5Oe`.

## Build & test

```bash
./gradlew build        # compile + run unit tests (first run downloads ~250 MB)
./gradlew test         # unit tests only
./gradlew runClient    # launch a dev client with the mod loaded
```

CI runs `./gradlew build` on every push (see `build.yml`); keep it green. A release is cut only
when the `VERSION` file changes (see below).

## Adding a module

1. Create the class under `module/impl/<category>/`, extending `Module` (or `BlockScanModule`
   for a radius ESP).
2. Call the `super(name, description, Category.X)` constructor.
3. Register settings in the constructor (see below).
4. Implement `onTick(client)` for per-tick logic, and/or register a `WorldRenderEvents` callback
   for rendering (gate it on `isEnabled()`; pass geometry in camera-relative space).
5. Register the module in `ModuleManager`'s constructor.

```java
public class Example extends Module {
    public Example() {
        super("Example", "Does a thing", Category.PLAYER);
        addBool("Enabled extra", true);
        addNumber("Range", 4.0, 1.0, 8.0, 0.5, false);
        addMode("Mode", "A", "A", "B", "C");
    }
    @Override public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        double range = Double.parseDouble(getSetting("Range")); // or read the typed object
    }
}
```

## Settings conventions

- Prefer the typed helpers `addBool` / `addNumber(name, def, min, max, step, integer)` / `addMode`
  so the setting is GUI-editable with sensible bounds.
- `addSetting(name, default)` still works and infers a type from the default; use it only for
  free-text values that shouldn't be cycled.
- Read values with `getSetting(name)` and parse defensively (`try/catch` → fallback) — never let a
  parse throw inside a tick/render callback.
- Don't restore enabled-state from disk; only setting *values* persist (via `ModuleConfig`).

## Performance rules

- **Never** scan the world (`getBlockState`/`getBlockEntity` loops, full entity sweeps) inside a
  render callback. Do it on a throttled tick and cache an immutable snapshot — extend
  `BlockScanModule` rather than re-implementing this.
- Bound any radius/range setting; clamp before use.

## Threading

- Anything written from a packet handler and read from render/GUI must be thread-safe
  (`volatile` field, synchronized collection, or an immutable snapshot). See `ServerInfo`.
- Do network/file IO off-thread (`CompletableFuture`), expose results via `volatile`.

## Mappings (Yarn) gotchas

- This repo has been bitten repeatedly by Yarn renames between MC versions. When a symbol is
  missing, check the current Yarn name rather than assuming the old one.
- For optional/renamable mixin targets use `@Inject(..., require = 0)` so a mapping change
  degrades gracefully instead of crashing.
- **Reflection by Yarn name does not work in the released JAR** (it's remapped to intermediary).
  Use real method calls; only use reflection for genuinely private fields and walk the class
  hierarchy (see `VanishTrackingMixin`, `Step`).

## Releasing

1. Add release notes at `.github/release-notes/v<X.Y.Z>.md`.
2. Bump `mod_version` in `gradle.properties` **and** the `VERSION` file to the same number.
3. Update the version in `README.md`.
4. Commit and push to the dev branch — `release.yml` tags `v<VERSION>`, builds, and publishes the
   JAR with your notes. Verify the release artifact is named `claudemc-<X.Y.Z>.jar`.

Do not open pull requests unless asked; work lands on the dev branch.
