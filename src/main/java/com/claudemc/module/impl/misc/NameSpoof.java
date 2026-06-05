package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Overrides the client-side display name shown above the player's own model.
 * Only cosmetic on the client side; does not change the name seen by others.
 * On servers with a display-name plugin you can pair this with a /nick command macro.
 */
public class NameSpoof extends Module {

    public static NameSpoof INSTANCE;

    public NameSpoof() {
        super("NameSpoof", "Shows a spoofed name above your character (client-side only)", Category.UTILITY);
        addSetting("Name", "Steve");
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        String spoofed = getSetting("Name").trim();
        if (!spoofed.isEmpty()) {
            try {
                var field = client.player.getClass().getSuperclass().getDeclaredField("customName");
                field.setAccessible(true);
                field.set(client.player, net.minecraft.text.Text.literal(spoofed));
                var visibleField = client.player.getClass().getSuperclass().getDeclaredField("customNameVisible");
                visibleField.setAccessible(true);
                visibleField.setBoolean(client.player, true);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDisable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        try {
            var field = client.player.getClass().getSuperclass().getDeclaredField("customName");
            field.setAccessible(true);
            field.set(client.player, null);
            var visibleField = client.player.getClass().getSuperclass().getDeclaredField("customNameVisible");
            visibleField.setAccessible(true);
            visibleField.setBoolean(client.player, false);
        } catch (Exception ignored) {}
    }
}
