package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.Random;

/**
 * AutoReconnect — automatically reconnects to the last server after disconnect.
 *
 * Optionally switches to a random offline alt before reconnecting, useful for
 * bypassing per-IP bans or resetting sessions on cracked servers.
 * Alt switching uses ClaudeMC's own AltManager.
 */
public class AutoReconnect extends Module {

    public static AutoReconnect INSTANCE;

    private String lastIp   = null;
    private int    lastPort = 25565;
    private int    delayLeft = 0;
    private boolean waiting  = false;

    private final Random rng = new Random();

    public AutoReconnect() {
        super("AutoReconnect", "Auto-reconnects after disconnect, optionally switching alt", Category.UTILITY);
        addNumber("DelayTicks",  100,  20, 1200,  20, true);   // default 5 s
        addNumber("RandomExtra",  60,   0,  600,  20, true);   // extra random ticks
        addBool("SwitchAlt",    false);                         // rotate offline alts
        INSTANCE = this;

        // Save last server on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (!isEnabled()) return;
            var si = client.getCurrentServerEntry();
            if (si != null) { lastIp = si.address; lastPort = 25565; waiting = true;
                delayLeft = parseInt(getSetting("DelayTicks"), 100)
                          + (parseInt(getSetting("RandomExtra"), 60) > 0
                             ? rng.nextInt(parseInt(getSetting("RandomExtra"), 60)) : 0);
            }
        });
    }

    @Override public void onDisable() { waiting = false; }

    @Override
    public void onTick(MinecraftClient client) {
        if (!waiting || lastIp == null || client.world != null) return;
        if (--delayLeft > 0) return;
        waiting = false;

        if (Boolean.parseBoolean(getSetting("SwitchAlt"))) {
            // Rotate to next offline alt if AltManager has any
            try {
                var altMgr = Class.forName("com.claudemc.gui.AltManager");
                var method = altMgr.getMethod("rotateOfflineAlt");
                method.invoke(null);
            } catch (Exception ignored) {}
        }

        // Reconnect
        ServerInfo info = new ServerInfo("AutoReconnect", lastIp, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(lastIp), info, false, null);
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
