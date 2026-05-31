package com.claudemc;

import com.claudemc.account.AltManager;
import com.claudemc.ai.AIConfig;
import com.claudemc.module.impl.misc.ForceOP;
import com.claudemc.chat.ChatOverlay;
import com.claudemc.chat.MacroManager;
import com.claudemc.gui.ClickGui;
import com.claudemc.hud.HudManager;
import com.claudemc.keybind.KeybindManager;
import com.claudemc.module.Module;
import com.claudemc.module.ModuleManager;
import com.claudemc.module.impl.render.BlockESP;
import com.claudemc.server.ExploitFetcher;
import com.claudemc.server.ExploitMatcher;
import com.claudemc.server.ServerInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class ClaudeMCClient implements ClientModInitializer {

    public static ModuleManager MODULES;
    public static HudManager    HUD;

    // Default chat overlay key: T (same key as vanilla chat, but works inside GUIs)
    public static final int DEFAULT_CHAT_OVERLAY_KEY = GLFW.GLFW_KEY_T;

    private static final int SCAN_DELAY_TICKS = 100;
    private int joinTick  = -1;
    private boolean alertSent = false;

    @Override
    public void onInitializeClient() {
        ForceOP.registerPayload();

        MODULES = new ModuleManager();
        HUD     = new HudManager();

        AIConfig.load();
        KeybindManager.INSTANCE.load();
        MacroManager.INSTANCE.load();
        AltManager.INSTANCE.load();
        com.claudemc.config.ModuleConfig.load(MODULES);
        if (BlockESP.INSTANCE != null) BlockESP.INSTANCE.loadCustomBlocksFull();

        ExploitFetcher.INSTANCE.fetchAsync();

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

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
        long window = client.getWindow().getHandle();
        int guiKey  = KeybindManager.INSTANCE.getGuiKey();

        // Chat overlay toggle key (works inside any GUI)
        if (isKeyJustPressed(window, DEFAULT_CHAT_OVERLAY_KEY) && client.currentScreen != null) {
            ChatOverlay.INSTANCE.toggle();
        }

        // GUI open key (only when no screen is open)
        if (client.currentScreen == null && isKeyJustPressed(window, guiKey)) {
            client.setScreen(new ClickGui());
        }

        if (client.player == null) return;

        // Per-module hotkeys
        for (Module m : MODULES.getModules()) {
            int bind = KeybindManager.INSTANCE.getModuleBind(m.getName());
            if (bind != -1 && client.currentScreen == null && isKeyJustPressed(window, bind)) {
                m.toggle();
            }
        }

        // Macro hotkeys (fire when no screen open)
        if (client.currentScreen == null) {
            for (MacroManager.Macro macro : MacroManager.INSTANCE.getMacros()) {
                if (macro.keybind != -1 && isKeyJustPressed(window, macro.keybind)) {
                    fireMacro(client, macro.command);
                }
            }
        }

        // BlockESP crosshair-add keybind (B key, no screen open)
        int addBlockKey = KeybindManager.INSTANCE.getBlockEspAddKey();
        if (client.currentScreen == null && isKeyJustPressed(window, addBlockKey)) {
            if (client.crosshairTarget instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && client.world != null
                    && BlockESP.INSTANCE != null) {
                var state = client.world.getBlockState(bhr.getBlockPos());
                String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
                boolean wasTracked = BlockESP.INSTANCE.getTargets().contains(id);
                if (wasTracked) {
                    BlockESP.INSTANCE.removeTarget(id);
                    client.player.sendMessage(Text.literal("§c[BlockESP] Removed: §7" + id), true);
                } else {
                    BlockESP.INSTANCE.addTarget(id);
                    client.player.sendMessage(Text.literal("§a[BlockESP] Added: §7" + id), true);
                }
                BlockESP.INSTANCE.saveCustomBlocks();
            }
        }

        MODULES.onTick(client);

        // Delayed exploit alert after joining
        if (joinTick >= 0) {
            joinTick++;
            if (joinTick >= SCAN_DELAY_TICKS && !alertSent) {
                alertSent = true;
                joinTick  = -1;
                runExploitAlert(client);
            }
        }
    }

    private void fireMacro(MinecraftClient client, String command) {
        if (command == null || command.isBlank()) return;
        if (command.startsWith("/")) {
            client.getNetworkHandler().sendChatCommand(command.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(command);
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
