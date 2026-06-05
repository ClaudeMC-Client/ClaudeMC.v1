package com.claudemc.config;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists ClickGUI panel positions and collapsed state to
 * {@code config/claudemc/gui.json} so the layout is remembered between sessions.
 */
public final class GuiConfig {

    private GuiConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE =
        new TypeToken<Map<String, int[]>>() {}.getType();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("claudemc/gui.json");
    }

    /**
     * Loads saved positions and collapsed flags into the provided maps.
     * Returns true if data was loaded (file existed), false if defaults should be used.
     * Format: { "COMBAT": [x, y, collapsed(0/1)], ... }
     */
    public static boolean load(Map<Category, int[]> panelPos, Map<Category, Boolean> collapsed) {
        Path p = path();
        if (!Files.exists(p)) return false;
        try (Reader r = Files.newBufferedReader(p)) {
            Map<String, int[]> data = GSON.fromJson(r, TYPE);
            if (data == null) return false;
            for (Category cat : Category.values()) {
                int[] entry = data.get(cat.name());
                if (entry != null && entry.length >= 3) {
                    panelPos.put(cat, new int[]{entry[0], entry[1]});
                    collapsed.put(cat, entry[2] != 0);
                }
            }
            return true;
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[GuiConfig] Failed to load: {}", e.getMessage());
            return false;
        }
    }

    /** Saves current positions and collapsed state to disk. */
    public static void save(Map<Category, int[]> panelPos, Map<Category, Boolean> collapsed) {
        Map<String, int[]> data = new LinkedHashMap<>();
        for (Category cat : Category.values()) {
            int[] pos = panelPos.get(cat);
            if (pos == null) continue;
            boolean col = collapsed.getOrDefault(cat, true);
            data.put(cat.name(), new int[]{pos[0], pos[1], col ? 1 : 0});
        }
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) { GSON.toJson(data, w); }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[GuiConfig] Failed to save: {}", e.getMessage());
        }
    }
}
