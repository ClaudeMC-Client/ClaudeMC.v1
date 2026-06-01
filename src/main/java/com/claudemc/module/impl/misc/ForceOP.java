package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;

public class ForceOP extends Module {

    /** Plugin-channel payload for the BungeeCord "ConnectOther" technique. */
    public record BungeeCordPayload(String subChannel, String player, String server)
            implements CustomPayload {
        public static final Id<BungeeCordPayload> ID =
            new Id<>(Identifier.of("bungeecord", "main"));
        public static final PacketCodec<PacketByteBuf, BungeeCordPayload> CODEC =
            PacketCodec.of(
                (value, buf) -> {
                    buf.writeString(value.subChannel());
                    buf.writeString(value.player());
                    buf.writeString(value.server());
                },
                buf -> new BungeeCordPayload(buf.readString(), buf.readString(), buf.readString())
            );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Called from ClaudeMCClient.onInitializeClient() to register the C2S payload type. */
    public static void registerPayload() {
        PayloadTypeRegistry.playC2S().register(BungeeCordPayload.ID, BungeeCordPayload.CODEC);
    }

    public ForceOP() {
        super("ForceOP", "Attempts to grant yourself OP on vulnerable servers", Category.MISC);
        addSetting("Technique", "All");
    }

    @Override
    public void onEnable() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) { setEnabled(false); return; }
        String name = client.player.getGameProfile().name();
        String tech = getSetting("Technique");
        if ("All".equals(tech) || "Command".equals(tech))    tryCommand(client, name);
        if ("All".equals(tech) || "BungeeCord".equals(tech)) tryBungeeCord(name);
        ClaudeMCMod.LOGGER.info("[ForceOP] Attempts fired for {}.", name);
        setEnabled(false);
    }

    private void tryCommand(MinecraftClient client, String name) {
        try { client.getNetworkHandler().sendChatCommand("op " + name); }
        catch (Exception e) { ClaudeMCMod.LOGGER.warn("[ForceOP][Command] {}", e.getMessage()); }
    }

    private void tryBungeeCord(String name) {
        try {
            ClientPlayNetworking.send(new BungeeCordPayload("ConnectOther", name, "hub"));
            ClaudeMCMod.LOGGER.info("[ForceOP][BungeeCord] Sent ConnectOther for {}.", name);
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[ForceOP][BungeeCord] {}", e.getMessage());
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}
