package com.claudemc;

import com.claudemc.gui.ClickGui;
import com.claudemc.hud.HudManager;
import com.claudemc.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ClaudeMCClient implements ClientModInitializer {

    public static ModuleManager MODULES;
    public static HudManager    HUD;

    public static KeyBinding keyOpenGui;

    @Override
    public void onInitializeClient() {
        MODULES = new ModuleManager();
        HUD     = new HudManager();

        keyOpenGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.claudemc.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            "category.claudemc"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyOpenGui.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ClickGui());
                }
            }
            if (client.player != null) {
                MODULES.onTick(client);
            }
        });

        HUD.register();
        ClaudeMCMod.LOGGER.info("ClaudeMC v2 initialised — press [.] to open GUI");
    }
}
