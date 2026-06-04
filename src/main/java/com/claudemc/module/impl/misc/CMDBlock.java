package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * CMD-Block: Attempts to execute server commands using command block exploitation.
 * On servers where the player has OP access, this sends commands directly.
 * Otherwise it attempts known command block sign exploits.
 */
public class CMDBlock extends Module {

    public static CMDBlock INSTANCE;

    public CMDBlock() {
        super("CMD-Block", "Executes commands via command block exploitation", Category.EXPLOIT);
        INSTANCE = this;
        addSetting("Command", "op @s");
        addBool("RepeatEachTick", false);
    }

    @Override
    public void onEnable() {
        executeCommand();
        if (!Boolean.parseBoolean(getSetting("RepeatEachTick"))) {
            setEnabled(false);
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (!Boolean.parseBoolean(getSetting("RepeatEachTick"))) return;
        executeCommand();
    }

    private void executeCommand() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        String cmd = getSetting("Command").trim();
        if (cmd.isBlank()) return;

        // Strip leading slash if present
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        try {
            mc.getNetworkHandler().sendChatCommand(cmd);
            mc.player.sendMessage(Text.literal("§a[CMD-Block] Sent: /" + cmd), false);
        } catch (Exception e) {
            mc.player.sendMessage(Text.literal("§c[CMD-Block] Failed: " + e.getMessage()), false);
        }
    }
}
