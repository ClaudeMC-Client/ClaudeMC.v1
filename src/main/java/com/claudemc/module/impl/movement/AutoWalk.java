package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * AutoWalk – automatically walks forward.
 * KeyboardInputMixin reads AutoWalk.INSTANCE.wantForward to inject the forward input.
 */
public class AutoWalk extends Module {

    public static AutoWalk INSTANCE;

    public volatile boolean wantForward = false;

    public AutoWalk() {
        super("AutoWalk", "Automatically walks forward", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        wantForward = true;
    }

    @Override
    public void onDisable() {
        wantForward = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Logic handled in KeyboardInputMixin via wantForward flag
    }
}
