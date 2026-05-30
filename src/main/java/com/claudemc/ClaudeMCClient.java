package com.claudemc;

import com.claudemc.gui.ClickGui;
import com.claudemc.hud.HudManager;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Module;
import com.claudemc.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class ClaudeMCClient implements ClientModInitializer {

    public static ModuleManager MODULES;
    public static HudManager    HUD;

    @Override
    public void onInitializeClient() {
        MODULES = new ModuleManager();
        HUD     = new HudManager();

        KeybindManager.INSTANCE.load();

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HUD.register();
        ClaudeMCMod.LOGGER.info("ClaudeMC v2 initialised — press [{}] to open GUI",
            KeybindManager.keyName(KeybindManager.INSTANCE.getGuiKey()));
    }

    private void onTick(MinecraftClient client) {
        // Check GUI open key
        long window = client.getWindow().getHandle();
        int guiKey  = KeybindManager.INSTANCE.getGuiKey();
        if (isKeyJustPressed(window, guiKey)) {
            if (client.currentScreen == null) {
                client.setScreen(new ClickGui());
            }
        }

        if (client.player == null) return;

        // Check per-module hotkeys
        for (Module m : MODULES.getModules()) {
            int bind = KeybindManager.INSTANCE.getModuleBind(m.getName());
            if (bind != -1 && client.currentScreen == null && isKeyJustPressed(window, bind)) {
                m.toggle();
            }
        }

        MODULES.onTick(client);
    }

    // ── Key detection ─────────────────────────────────────────────────────

    // Track which keys were down last tick so we fire on the press edge only
    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();

    private boolean isKeyJustPressed(long window, int key) {
        boolean down = InputUtil.isKeyPressed(window, key);
        if (down && heldKeys.add(key)) return true;   // newly pressed
        if (!down) heldKeys.remove(key);
        return false;
    }
}
