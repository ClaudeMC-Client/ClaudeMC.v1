package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * MassTPA: Sends /tpa to all online players on the server.
 */
public class MassTPA extends Module {

    public static MassTPA INSTANCE;

    private final List<String> pendingPlayers = new ArrayList<>();
    private int tickCounter = 0;
    private boolean started = false;

    public MassTPA() {
        super("MassTPA", "Sends /tpa to all online players", Category.EXPLOIT);
        INSTANCE = this;
        addNumber("DelayTicks", 20, 5, 200, 5, true);
        addMode("Command", "tpa", "tpa", "tpaccept", "tpahere");
    }

    @Override
    public void onEnable() {
        pendingPlayers.clear();
        tickCounter = 0;
        started = false;
    }

    @Override
    public void onDisable() {
        pendingPlayers.clear();
        tickCounter = 0;
        started = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (!started) {
            // Collect all online players except self
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().name();
                if (name != null && !name.equals(mc.player.getGameProfile().name())) {
                    pendingPlayers.add(name);
                }
            }
            started = true;
            mc.player.sendMessage(Text.literal("§a[MassTPA] Sending TPA to " + pendingPlayers.size() + " players..."), false);
        }

        if (pendingPlayers.isEmpty()) {
            mc.player.sendMessage(Text.literal("§a[MassTPA] Done!"), false);
            setEnabled(false);
            return;
        }

        int delay = parseInt(getSetting("DelayTicks"), 20);
        if (++tickCounter < delay) return;
        tickCounter = 0;

        String player = pendingPlayers.remove(0);
        String cmd = getSetting("Command");
        mc.getNetworkHandler().sendChatCommand(cmd + " " + player);
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
