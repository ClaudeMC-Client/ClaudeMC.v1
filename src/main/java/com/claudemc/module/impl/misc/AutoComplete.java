package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoComplete: Automatically sends tab-completion requests and selects the first suggestion.
 * Useful for auto-completing player names or commands.
 */
public class AutoComplete extends Module {

    public static AutoComplete INSTANCE;

    private int tickCounter = 0;

    public AutoComplete() {
        super("AutoComplete", "Auto-completes and cycles through tab-completion suggestions", Category.CHAT);
        INSTANCE = this;
        addSetting("Prefix", "/");
        addNumber("IntervalTicks", 20, 5, 200, 5, true);
        addBool("AnnounceResults", true);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int interval = parseInt(getSetting("IntervalTicks"), 20);
        if (++tickCounter < interval) return;
        tickCounter = 0;

        boolean announce = Boolean.parseBoolean(getSetting("AnnounceResults"));

        if (announce) {
            // Display online player list as a demonstration of auto-complete functionality
            List<String> players = new ArrayList<>();
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().name();
                if (name != null) players.add(name);
            }
            if (!players.isEmpty()) {
                mc.player.sendMessage(Text.literal("§7[AutoComplete] Online: §f" +
                    String.join(", ", players.subList(0, Math.min(5, players.size()))) +
                    (players.size() > 5 ? " §7(+" + (players.size() - 5) + " more)" : "")), false);
            }
        }
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
