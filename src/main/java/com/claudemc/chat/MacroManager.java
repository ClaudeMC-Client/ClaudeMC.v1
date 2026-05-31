package com.claudemc.chat;

import com.claudemc.ClaudeMCMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

/**
 * Manages macro entries — each macro is a name, a command/chat string, and an optional keybind.
 * Saved to .minecraft/config/claudemc/macros.json.
 */
public class MacroManager {

    public static final MacroManager INSTANCE = new MacroManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/macros.json");
    private static final Type LIST_TYPE = new TypeToken<List<Macro>>() {}.getType();

    public static class Macro {
        public String name;
        public String command;   // e.g. "/tp spawn" or "hello world"
        public int    keybind;   // GLFW key code, -1 = none
        public Macro() { keybind = -1; }
        public Macro(String name, String command, int keybind) {
            this.name = name; this.command = command; this.keybind = keybind;
        }
    }

    private final List<Macro> macros = new ArrayList<>();

    private MacroManager() {}

    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            List<Macro> loaded = GSON.fromJson(r, LIST_TYPE);
            if (loaded != null) { macros.clear(); macros.addAll(loaded); }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[MacroManager] Load failed: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) { GSON.toJson(macros, w); }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[MacroManager] Save failed: {}", e.getMessage());
        }
    }

    public List<Macro> getMacros() { return macros; }

    public void add(String name, String command, int keybind) {
        macros.add(new Macro(name, command, keybind));
        save();
    }

    public void remove(int index) {
        if (index >= 0 && index < macros.size()) { macros.remove(index); save(); }
    }

    public void update(int index, String name, String command, int keybind) {
        if (index < 0 || index >= macros.size()) return;
        Macro m = macros.get(index);
        m.name = name; m.command = command; m.keybind = keybind;
        save();
    }
}
