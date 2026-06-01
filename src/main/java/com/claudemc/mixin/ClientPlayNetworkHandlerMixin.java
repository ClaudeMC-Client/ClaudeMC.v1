package com.claudemc.mixin;

import com.claudemc.hud.HudManager;
import com.claudemc.module.impl.misc.AuthMeBypass;
import com.claudemc.server.ServerInfo;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.message.MessageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void claudemc$onTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        HudManager.onWorldTimeUpdate();
    }

    /** Intercept custom payload to detect server brand and plugin channels. */
    @Inject(method = "onCustomPayload", at = @At("HEAD"), require = 0)
    private void claudemc$onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        try {
            var payload = packet.payload();
            if (payload == null) return;
            String id = payload.getId().toString();

            // Brand packet: "minecraft:brand"
            if ("minecraft:brand".equals(id)) {
                // Payload bytes: VarInt length-prefixed UTF-8 string
                // Use reflection to get the raw bytes from the payload
                try {
                    var clazz = payload.getClass();
                    // BrandCustomPayload stores a String brand field
                    for (var f : clazz.getDeclaredFields()) {
                        if (f.getType() == String.class) {
                            f.setAccessible(true);
                            String brand = (String) f.get(payload);
                            if (brand != null) ServerInfo.INSTANCE.setBrand(brand);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                return;
            }

            // Channel registration: "minecraft:register"
            if ("minecraft:register".equals(id)) {
                try {
                    var clazz = payload.getClass();
                    for (var f : clazz.getDeclaredFields()) {
                        f.setAccessible(true);
                        Object val = f.get(payload);
                        if (val instanceof java.util.Collection<?> col) {
                            java.util.List<String> channels = new java.util.ArrayList<>();
                            for (Object o : col) channels.add(o.toString());
                            ServerInfo.INSTANCE.addChannels(channels);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Intercept the proxy's /server tab-complete response and forward to AuthMeBypass.
     * The proxy (BungeeCord/Velocity) fills in available backend server names.
     */
    @Inject(method = "onCommandSuggestions", at = @At("HEAD"), require = 0)
    private void claudemc$onCommandSuggestions(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
        try {
            AuthMeBypass ab = AuthMeBypass.INSTANCE;
            if (ab == null || !ab.isEnabled()) return;
            java.util.List<String> names = new java.util.ArrayList<>();
            for (com.mojang.brigadier.suggestion.Suggestion s : packet.getSuggestions().getList())
                names.add(s.getText());
            ab.onTabCompletions(packet.id(), names);
        } catch (Exception ignored) {}
    }

    /** Reset server info when we disconnect. */
    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 0)
    private void claudemc$onDisconnect(DisconnectS2CPacket packet, CallbackInfo ci) {
        ServerInfo.INSTANCE.reset();
    }
}
