package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Allows movement while a GUI (inventory, chest, etc.) is open.
 * The actual key-state restoration is handled by KeyboardInputMixin which
 * checks InventoryMove.INSTANCE.isEnabled() and reads GLFW key states directly.
 */
public class InventoryMove extends Module {

    public static InventoryMove INSTANCE;

    public InventoryMove() {
        super("InventoryMove", "Allows WASD movement while inventory/GUIs are open", Category.MOVEMENT);
        addBool("Sprint", true);
        addBool("Jump",   true);
        INSTANCE = this;
    }

    public boolean allowSprint() { return Boolean.parseBoolean(getSetting("Sprint")); }
    public boolean allowJump()   { return Boolean.parseBoolean(getSetting("Jump")); }

    @Override public void onTick(MinecraftClient client) {}
}
