package com.claudemc.keybind;

import com.claudemc.ClaudeMCMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores and persists keybinds for every module plus the GUI open key.
 * Saved to .minecraft/claudemc/keybinds.json.
 */
public class KeybindManager {

    public static final KeybindManager INSTANCE = new KeybindManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("claudemc/keybinds.json");

    // module name → GLFW key code (-1 = no bind)
    private final Map<String, Integer> moduleBind = new HashMap<>();
    private int guiKey           = GLFW.GLFW_KEY_PERIOD;
    private int blockEspAddKey   = GLFW.GLFW_KEY_B;

    private KeybindManager() {}

    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = GSON.fromJson(r, type);
            if (data == null) return;

            Object gk = data.get("__gui__");
            if (gk instanceof Number n) guiKey = n.intValue();

            Object bk = data.get("__blockespAdd__");
            if (bk instanceof Number n) blockEspAddKey = n.intValue();

            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!e.getKey().startsWith("__") && e.getValue() instanceof Number n) {
                    moduleBind.put(e.getKey(), n.intValue());
                }
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[KeybindManager] Failed to load keybinds: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, Integer> data = new HashMap<>(moduleBind);
            data.put("__gui__", guiKey);
            data.put("__blockespAdd__", blockEspAddKey);
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[KeybindManager] Failed to save keybinds: {}", e.getMessage());
        }
    }

    /** Returns the GLFW key code for a module, or -1 if none is set. */
    public int getModuleBind(String moduleName) {
        return moduleBind.getOrDefault(moduleName, -1);
    }

    public void setModuleBind(String moduleName, int glfwKey) {
        if (glfwKey == -1 || glfwKey == GLFW.GLFW_KEY_UNKNOWN) {
            moduleBind.remove(moduleName);
        } else {
            moduleBind.put(moduleName, glfwKey);
        }
        save();
    }

    public int getGuiKey() { return guiKey; }

    public void setGuiKey(int glfwKey) {
        guiKey = (glfwKey == -1) ? GLFW.GLFW_KEY_PERIOD : glfwKey;
        save();
    }

    public int getBlockEspAddKey() { return blockEspAddKey; }

    public void setBlockEspAddKey(int glfwKey) {
        blockEspAddKey = (glfwKey == -1) ? GLFW.GLFW_KEY_B : glfwKey;
        save();
    }

    /** Human-readable name for a GLFW key code. */
    public static String keyName(int key) {
        if (key == -1 || key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) return name.toUpperCase();
        // Fallback for special keys
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE        -> "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT   -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT  -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL-> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT     -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT    -> "RALT";
            case GLFW.GLFW_KEY_TAB          -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK    -> "CAPS";
            case GLFW.GLFW_KEY_ESCAPE       -> "ESC";
            case GLFW.GLFW_KEY_ENTER        -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE    -> "BACKSPACE";
            case GLFW.GLFW_KEY_INSERT       -> "INSERT";
            case GLFW.GLFW_KEY_DELETE       -> "DELETE";
            case GLFW.GLFW_KEY_HOME         -> "HOME";
            case GLFW.GLFW_KEY_END          -> "END";
            case GLFW.GLFW_KEY_PAGE_UP      -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN    -> "PGDN";
            case GLFW.GLFW_KEY_UP           -> "UP";
            case GLFW.GLFW_KEY_DOWN         -> "DOWN";
            case GLFW.GLFW_KEY_LEFT         -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT        -> "RIGHT";
            case GLFW.GLFW_KEY_F1  -> "F1";  case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";  case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";  case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";  case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";  case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "KEY_" + key;
        };
    }
}
