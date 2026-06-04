package com.claudemc.module.impl.world;

import com.claudemc.ClaudeMCClient;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class Panic extends Module {

    public static Panic INSTANCE;

    public Panic() {
        super("Panic", "Disables all modules and disconnects from the server", Category.WORLD);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Disable all other modules
        if (ClaudeMCClient.MODULES != null) {
            for (Module mod : ClaudeMCClient.MODULES.getModules()) {
                if (mod != this && mod.isEnabled()) {
                    mod.setEnabled(false);
                }
            }
        }

        // Disconnect from server
        if (mc.world != null) {
            mc.world.disconnect();
        }
        mc.disconnect();

        setEnabled(false);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        // All work done in onEnable
    }
}
