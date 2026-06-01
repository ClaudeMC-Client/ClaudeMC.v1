package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.*;

/**
 * ForeachCmd — runs a command for each online player, or N times, with a random delay.
 *
 * Usage (set Command setting):
 *   Player mode  — command contains %player% → runs once per online player
 *   Repeat mode  — command contains %i%      → runs Iterations times
 *   Both missing → plain command runs Iterations times
 *
 * Inspired by AntiP2W Tools' .foreach command.
 */
public class ForeachCmd extends Module {

    public static ForeachCmd INSTANCE;

    private final Queue<String> queue = new ArrayDeque<>();
    private int delayLeft = 0;

    public ForeachCmd() {
        super("ForeachCmd",
              "Runs a command for each player (%player%) or N times (%i%). Toggle to send queue.",
              Category.MISC);
        addSetting("Command",    "/say hello %player%");
        addNumber("Iterations",  10, 1, 200, 1, true);
        addNumber("MinDelay",    20, 1, 200, 1, true);   // ticks
        addNumber("MaxDelay",    40, 1, 400, 1, true);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        queue.clear();
        var client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        String cmd = getSetting("Command");
        int iterations = parseInt(getSetting("Iterations"), 10);

        if (cmd.contains("%player%")) {
            // One command per online player
            if (client.getNetworkHandler() != null) {
                for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
                    String name = e.getProfile().name();
                    queue.add(cmd.replace("%player%", name));
                }
            }
        } else {
            for (int i = 1; i <= iterations; i++)
                queue.add(cmd.replace("%i%", String.valueOf(i)));
        }

        client.player.sendMessage(
            Text.literal("§6[ForeachCmd] §7Queued " + queue.size() + " commands."), false);
        delayLeft = 0;
    }

    @Override
    public void onDisable() { queue.clear(); }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (queue.isEmpty()) { setEnabled(false); return; }
        if (--delayLeft > 0) return;

        String cmd = queue.poll();
        if (cmd != null) {
            if (cmd.startsWith("/")) client.getNetworkHandler().sendChatCommand(cmd.substring(1));
            else client.getNetworkHandler().sendChatMessage(cmd);
        }

        int min = parseInt(getSetting("MinDelay"), 20);
        int max = parseInt(getSetting("MaxDelay"), 40);
        delayLeft = min + (max > min ? (int)(Math.random() * (max - min)) : 0);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
