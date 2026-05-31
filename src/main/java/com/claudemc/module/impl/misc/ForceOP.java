package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.CommandBlockExecutor;
import io.netty.buffer.Unpooled;

public class ForceOP extends Module {

    public ForceOP() {
        super("ForceOP",
              "Attempts to grant yourself OP on vulnerable servers",
              Category.MISC);
        addSetting("Technique", "All");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            setEnabled(false);
            return;
        }

        String name      = client.player.getGameProfile().getName();
        String technique = getSetting("Technique");

        if ("All".equals(technique) || "Command".equals(technique))   tryCommand(client, name);
        if ("All".equals(technique) || "CMDBlock".equals(technique))  tryCommandBlock(client, name);
        if ("All".equals(technique) || "BungeeCord".equals(technique)) tryBungeeCord(client, name);

        ClaudeMCMod.LOGGER.info("[ForceOP] Attempts fired for {}.", name);
        setEnabled(false);
    }

    private void tryCommand(MinecraftClient client, String name) {
        try {
            client.getNetworkHandler().sendChatCommand("op " + name);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][Command] {}", e.getMessage());
        }
    }

    private void tryCommandBlock(MinecraftClient client, String name) {
        try {
            client.getNetworkHandler().sendPacket(
                new UpdateCommandBlockC2SPacket(
                    new BlockPos(0, 0, 0),
                    "/op " + name,
                    CommandBlockExecutor.Type.SEQUENCE,
                    false, false, true
                )
            );
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][CMDBlock] {}", e.getMessage());
        }
    }

    private void tryBungeeCord(MinecraftClient client, String name) {
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString("ConnectOther");
            buf.writeString(name);
            buf.writeString("hub");
            // Plugin-message packets changed in 1.20.5 — send raw via channel if needed
            // For now this is a no-op until a compatible payload wrapper is available
            buf.release();
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][BungeeCord] {}", e.getMessage());
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}
