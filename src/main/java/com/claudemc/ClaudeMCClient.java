package com.claudemc;

import com.claudemc.gui.ClickGui;
import com.claudemc.hud.HudManager;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Module;
import com.claudemc.module.ModuleManager;
import com.claudemc.server.ExploitFetcher;
import com.claudemc.server.ExploitMatcher;
import com.claudemc.server.ServerInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class ClaudeMCClient implements ClientModInitializer {

    public static ModuleManager MODULES;
    public static HudManager    HUD;

    // Ticks to wait after joining before running exploit match (give server time to send channels)
    private static final int SCAN_DELAY_TICKS = 100; // ~5 seconds
    private int joinTick = -1;
    private boolean alertSent = false;

    @Override
    public void onInitializeClient() {
        MODULES = new ModuleManager();
        HUD     = new HudManager();

        KeybindManager.INSTANCE.load();

        // Kick off remote exploit DB fetch immediately on startup (background thread)
        ExploitFetcher.INSTANCE.fetchAsync();

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // Reset server info on join; start scan countdown
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo.INSTANCE.reset();
            joinTick  = 0;
            alertSent = false;
        });

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

        // After joining, wait SCAN_DELAY_TICKS then run exploit match and notify
        if (joinTick >= 0) {
            joinTick++;
            if (joinTick >= SCAN_DELAY_TICKS && !alertSent) {
                alertSent = true;
                joinTick  = -1;
                runExploitAlert(client);
            }
        }
    }

    private void runExploitAlert(MinecraftClient client) {
        if (!ExploitFetcher.INSTANCE.isLoaded()) return;
        long confirmed = ExploitMatcher.match(ServerInfo.INSTANCE).stream()
            .filter(ExploitMatcher.MatchResult::confirmed).count();
        if (confirmed > 0) {
            client.player.sendMessage(
                Text.literal("§8[§cClaudeMC§8] §6⚠ " + confirmed +
                    " confirmed exploit" + (confirmed == 1 ? "" : "s") +
                    " found on this server! §7Open §f[Server Info]§7 for details."),
                false);
        }
    }

    // ── Key detection ─────────────────────────────────────────────────────

    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();

    private boolean isKeyJustPressed(long window, int key) {
        boolean down = InputUtil.isKeyPressed(window, key);
        if (down && heldKeys.add(key)) return true;
        if (!down) heldKeys.remove(key);
        return false;
    }
}
