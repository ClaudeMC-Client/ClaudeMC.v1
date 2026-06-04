package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;

/**
 * Automatically fills in and submits sign text when a sign edit screen opens.
 * Uses reflection to access the sign screen's text field.
 */
public class AutoSign extends Module {

    public static AutoSign INSTANCE;

    public AutoSign() {
        super("AutoSign", "Automatically signs signs with configured text", Category.PLAYER);
        addSetting("Line1", "ClaudeMC");
        addSetting("Line2", "");
        addSetting("Line3", "");
        addSetting("Line4", "");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!(client.currentScreen instanceof AbstractSignEditScreen screen)) return;

        try {
            // Access the sign and set messages via reflection
            var clazz  = AbstractSignEditScreen.class;
            var msgField = clazz.getDeclaredField("messages");
            msgField.setAccessible(true);
            String[] messages = (String[]) msgField.get(screen);

            messages[0] = getSetting("Line1");
            messages[1] = getSetting("Line2");
            messages[2] = getSetting("Line3");
            messages[3] = getSetting("Line4");

            // Find and invoke the finish method
            var finishMethod = clazz.getDeclaredMethod("finishEditing");
            finishMethod.setAccessible(true);
            finishMethod.invoke(screen);
        } catch (Exception e) {
            // Field/method name may differ per mapping — silently ignore
        }
    }
}
