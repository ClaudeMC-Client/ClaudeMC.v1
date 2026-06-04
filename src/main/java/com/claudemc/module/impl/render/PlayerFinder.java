package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * PlayerFinder — lists all online players and their approximate coordinates.
 * Players within render distance are shown with their exact position;
 * players outside render distance are shown as "out of range" but still listed.
 * On enable, all online players are announced in chat.
 */
public class PlayerFinder extends Module {

    public static PlayerFinder INSTANCE;

    private int cooldown = 0;

    public PlayerFinder() {
        super("PlayerFinder", "Finds players on the server and shows their coordinates", Category.RENDER);
        addSetting("Interval", "100"); // ticks between refreshes
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;
        client.player.sendMessage(Text.literal("§b[PlayerFinder] §7Scanning online players..."), false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return;
        if (--cooldown > 0) return;
        cooldown = parseInt(getSetting("Interval"), 100);

        var handler    = client.getNetworkHandler();
        var playerList = handler.getPlayerList();
        var self       = client.player;

        for (var entry : playerList) {
            UUID uuid = entry.getProfile().id();
            String name = entry.getProfile().name();
            if (uuid.equals(self.getUuid())) continue;

            // Try to find in world
            var entity = client.world.getPlayers().stream()
                .filter(p -> p.getUuid().equals(uuid))
                .findFirst().orElse(null);

            if (entity != null) {
                var pos = entity.getBlockPos();
                client.player.sendMessage(
                    Text.literal(String.format("§b[PlayerFinder] §f%s §7at §f%d, %d, %d",
                        name, pos.getX(), pos.getY(), pos.getZ())), false);
            } else {
                client.player.sendMessage(
                    Text.literal("§b[PlayerFinder] §f" + name + " §7is online (out of range)"), false);
            }
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
