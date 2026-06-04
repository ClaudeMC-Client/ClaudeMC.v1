package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * FancyChat: Adds decorative prefix/suffix formatting to chat messages.
 * Note: Requires mixin injection into chat sending for full functionality.
 * This module stores settings; the actual injection is handled by ChatMixin.
 */
public class FancyChat extends Module {

    public static FancyChat INSTANCE;

    public FancyChat() {
        super("FancyChat", "Adds decorative formatting to chat messages", Category.CHAT);
        INSTANCE = this;
        addSetting("Prefix", "✦ ");
        addSetting("Suffix", " ✦");
        addMode("Style", "Brackets", "Brackets", "Stars", "Arrows", "Custom");
        addBool("ColourPrefix", true);
    }

    /**
     * Called externally (e.g. from a mixin) to format a message before sending.
     */
    public String formatMessage(String original) {
        if (!isEnabled()) return original;

        String style = getSetting("Style");
        boolean colour = Boolean.parseBoolean(getSetting("ColourPrefix"));

        String prefix, suffix;
        switch (style) {
            case "Stars"   -> { prefix = "★ "; suffix = " ★"; }
            case "Arrows"  -> { prefix = "» "; suffix = " «"; }
            case "Brackets"-> { prefix = "[ "; suffix = " ]"; }
            case "Custom"  -> { prefix = getSetting("Prefix"); suffix = getSetting("Suffix"); }
            default        -> { prefix = getSetting("Prefix"); suffix = getSetting("Suffix"); }
        }

        if (colour) {
            prefix = "§b" + prefix + "§r";
            suffix = "§b" + suffix + "§r";
        }

        return prefix + original + suffix;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        // Passive module — logic triggered via formatMessage()
    }
}
