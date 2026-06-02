package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

public class ChatSpammer extends Module {

    private int tickCounter = 0;

    public ChatSpammer() {
        super("ChatSpammer", "Sends a chat message on a configurable interval", Category.CHAT);
        addSetting("Message",      "Hello world!");
        addNumber("IntervalTicks", 200, 20, 1200, 20, true);
        addBool("UseCommand",      false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (client.currentScreen != null) return;

        if (++tickCounter < parseInt(getSetting("IntervalTicks"), 200)) return;
        tickCounter = 0;

        String msg = getSetting("Message");
        if (msg.isBlank()) return;

        if (Boolean.parseBoolean(getSetting("UseCommand")) && msg.startsWith("/")) {
            client.getNetworkHandler().sendChatCommand(msg.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(msg);
        }
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
