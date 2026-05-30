package com.claudemc;

import com.claudemc.gui.LunarMenuScreen;
import com.claudemc.hud.HudOverlay;
import com.claudemc.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ClaudeMCClient implements ClientModInitializer {

    public static ModuleManager MODULE_MANAGER;
    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        MODULE_MANAGER = new ModuleManager();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.claudemc.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            "category.claudemc"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new LunarMenuScreen());
                }
            }
            if (client.player != null) {
                MODULE_MANAGER.onTick(client);
            }
        });

        HudOverlay.register();
        ClaudeMCMod.LOGGER.info("ClaudeMC client initialised — press [.] to open menu");
    }
}
