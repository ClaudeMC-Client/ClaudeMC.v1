package com.claudemc.config;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Module;
import com.claudemc.module.ModuleManager;
import com.claudemc.module.setting.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists per-module setting values to {@code config/claudemc/modules.json}.
 *
 * Only setting <em>values</em> are stored — module enabled-state is intentionally not
 * restored on startup, because firing {@code onEnable()} before a world is loaded is unsafe
 * for some modules. Settings are saved whenever they are edited in the ClickGUI.
 */
public final class ModuleConfig {

    private ModuleConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE =
        new TypeToken<Map<String, Map<String, String>>>() {}.getType();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("claudemc/modules.json");
    }

    /** Applies saved setting values onto the registered modules. Missing entries are ignored. */
    public static void load(ModuleManager manager) {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            Map<String, Map<String, String>> data = GSON.fromJson(r, TYPE);
            if (data == null) return;
            for (Module m : manager.getModules()) {
                Map<String, String> values = data.get(m.getName());
                if (values == null) continue;
                for (Setting s : m.getSettings()) {
                    String v = values.get(s.getName());
                    if (v != null) s.fromString(v);
                }
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ModuleConfig] Failed to load module settings: {}", e.getMessage());
        }
    }

    /** Writes the current setting values of every module to disk. */
    public static void save(ModuleManager manager) {
        Map<String, Map<String, String>> data = new LinkedHashMap<>();
        for (Module m : manager.getModules()) {
            if (m.getSettings().isEmpty()) continue;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : m.getSettings()) values.put(s.getName(), s.asString());
            data.put(m.getName(), values);
        }
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) { GSON.toJson(data, w); }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ModuleConfig] Failed to save module settings: {}", e.getMessage());
        }
    }
}
