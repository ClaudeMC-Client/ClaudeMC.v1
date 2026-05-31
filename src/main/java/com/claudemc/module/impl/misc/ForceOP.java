package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;

public class ForceOP extends Module {

    public ForceOP() {
        super("ForceOP", "Attempts to grant yourself OP on vulnerable servers", Category.MISC);
        addSetting("Technique", "All");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) { setEnabled(false); return; }
        String name = client.player.getGameProfile().getName();
        String tech = getSetting("Technique");
        if ("All".equals(tech) || "Command".equals(tech))    tryCommand(client, name);
        if ("All".equals(tech) || "BungeeCord".equals(tech)) tryBungeeCord(client, name);
        ClaudeMCMod.LOGGER.info("[ForceOP] Attempts fired for {}.", name);
        setEnabled(false);
    }

    private void tryCommand(MinecraftClient client, String name) {
        try { client.getNetworkHandler().sendChatCommand("op " + name); }
        catch (Exception e) { ClaudeMCMod.LOGGER.warn("[ForceOP][Command] {}", e.getMessage()); }
    }

    private void tryBungeeCord(MinecraftClient client, String name) {
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString("ConnectOther");
            buf.writeString(name);
            buf.writeString("hub");
            buf.release();
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][BungeeCord] {}", e.getMessage());
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}
