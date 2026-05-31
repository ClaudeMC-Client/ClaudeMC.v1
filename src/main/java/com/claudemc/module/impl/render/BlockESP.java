package com.claudemc.module.impl.render;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BlockESP extends Module {

    public static BlockESP INSTANCE;

    private static final Set<String> DEFAULT_TARGETS = new HashSet<>(Arrays.asList(
        "minecraft:shulker_box",        "minecraft:white_shulker_box",
        "minecraft:orange_shulker_box", "minecraft:magenta_shulker_box",
        "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box",
        "minecraft:lime_shulker_box",   "minecraft:pink_shulker_box",
        "minecraft:gray_shulker_box",   "minecraft:light_gray_shulker_box",
        "minecraft:cyan_shulker_box",   "minecraft:purple_shulker_box",
        "minecraft:blue_shulker_box",   "minecraft:brown_shulker_box",
        "minecraft:green_shulker_box",  "minecraft:red_shulker_box",
        "minecraft:black_shulker_box",
        "minecraft:chest",              "minecraft:trapped_chest",
        "minecraft:ender_chest",        "minecraft:barrel",
        "minecraft:spawner",            "minecraft:ancient_debris"
    ));

    private final Set<String> targets = new HashSet<>(DEFAULT_TARGETS);

    // Key the user can press while looking at a block to add it (default: B)
    public static final int DEFAULT_ADD_KEY = org.lwjgl.glfw.GLFW.GLFW_KEY_B;

    // Scanning the world is expensive, so we do it on a throttled tick rather than
    // on every rendered frame. The render event only draws this cached snapshot.
    private static final int SCAN_INTERVAL_TICKS = 8;   // rescan ~2.5x/second
    private static final int MAX_RADIUS          = 64;  // hard cap to bound scan cost
    private int scanCooldown = 0;
    private volatile List<Found> found = Collections.emptyList();

    /** A matched block position with its precomputed highlight colour. */
    private record Found(int x, int y, int z, float r, float g, float b) {}

    public BlockESP() {
        super("BlockESP", "Highlights shulkers, chests, spawners through walls", Category.RENDER);
        addSetting("Radius", "32");
        INSTANCE = this;

        loadCustomBlocks();

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam      = context.camera().getPos();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            // Draw the cached snapshot only — no world scanning on the render path.
            for (Found f : INSTANCE.found) {
                double bx = f.x() - cam.x, by = f.y() - cam.y, bz = f.z() - cam.z;
                Box box = new Box(bx, by, bz, bx + 1, by + 1, bz + 1).expand(0.01);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, f.r(), f.g(), f.b(), 1f);
            }
        });
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("claudemc/blockesp.json");
    }

    public void loadCustomBlocks() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            List<String> saved = new Gson().fromJson(Files.readString(p),
                new TypeToken<List<String>>(){}.getType());
            if (saved != null) targets.addAll(saved);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[BlockESP] Failed to load custom blocks: {}", e.getMessage());
        }
    }

    public void saveCustomBlocks() {
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            // Save only the blocks that are NOT defaults (so defaults stay defaults)
            Set<String> custom = new HashSet<>(targets);
            custom.removeAll(DEFAULT_TARGETS);
            // Also record removed defaults
            Set<String> removedDefaults = new HashSet<>(DEFAULT_TARGETS);
            removedDefaults.removeAll(targets);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("custom",         new ArrayList<>(custom));
            data.put("removedDefaults", new ArrayList<>(removedDefaults));
            Files.writeString(p, new Gson().toJson(data));
        } catch (IOException e) {
            ClaudeMCMod.LOGGER.warn("[BlockESP] Failed to save custom blocks: {}", e.getMessage());
        }
    }

    // Properly load with removed-defaults support
    @SuppressWarnings("unchecked")
    public void loadCustomBlocksFull() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            String json = Files.readString(p);
            // Try new map format first
            try {
                Map<String, Object> data = new Gson().fromJson(json, Map.class);
                if (data.containsKey("custom")) {
                    List<String> custom = (List<String>) data.get("custom");
                    if (custom != null) targets.addAll(custom);
                }
                if (data.containsKey("removedDefaults")) {
                    List<String> removed = (List<String>) data.get("removedDefaults");
                    if (removed != null) targets.removeAll(removed);
                }
            } catch (Exception e2) {
                // Old plain-list format
                List<String> saved = new Gson().fromJson(json, new TypeToken<List<String>>(){}.getType());
                if (saved != null) targets.addAll(saved);
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[BlockESP] Failed to load custom blocks: {}", e.getMessage());
        }
    }

    // ── API ───────────────────────────────────────────────────────────────

    public void addTarget(String id)    { targets.add(id); }
    public void removeTarget(String id) { targets.remove(id); }
    public Set<String> getTargets()     { return targets; }

    private float[] colorForBlock(Block b) {
        if (b instanceof ShulkerBoxBlock)                         return new float[]{0.8f, 0.2f, 0.8f};
        if (b instanceof ChestBlock || b instanceof BarrelBlock)  return new float[]{0.9f, 0.7f, 0.1f};
        if (b instanceof SpawnerBlock)                            return new float[]{1.0f, 0.2f, 0.2f};
        return new float[]{0.5f, 1.0f, 1.0f};
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (--scanCooldown > 0) return;
        scanCooldown = SCAN_INTERVAL_TICKS;

        int radius    = Math.min(MAX_RADIUS, parseInt(getSetting("Radius"), 32));
        var playerPos = client.player.getBlockPos();
        var registry  = net.minecraft.registry.Registries.BLOCK;

        List<Found> results = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(client.world.getBottomY(), playerPos.getY() - radius);
        int maxY = Math.min(client.world.getTopY(),    playerPos.getY() + radius);

        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    var state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String id = registry.getId(state.getBlock()).toString();
                    if (!targets.contains(id)) continue;
                    float[] col = colorForBlock(state.getBlock());
                    results.add(new Found(x, y, z, col[0], col[1], col[2]));
                }
            }
        }
        found = results;   // publish snapshot for the render path
    }

    int parseInt(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
}
