package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Replaces the player's own username with a generic placeholder in chat messages.
 * Works by intercepting rendering — see NameProtectMixin for chat message processing.
 */
public class NameProtect extends Module {

    public static NameProtect INSTANCE;

    public NameProtect() {
        super("NameProtect", "Replaces own name with 'Player' in chat", Category.CHAT);
        addSetting("ReplaceName", "Player");
        INSTANCE = this;
    }

    /** Returns the replacement label. */
    public String getReplacement() {
        String r = getSetting("ReplaceName").trim();
        return r.isEmpty() ? "Player" : r;
    }

    /**
     * Replaces occurrences of the player's name in a Text component string.
     * Called from the mixin.
     */
    public String processString(String input, String realName) {
        if (input == null || realName == null || realName.isEmpty()) return input;
        return input.replace(realName, getReplacement());
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
