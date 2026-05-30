package com.claudemc.mixin;

import com.claudemc.hud.HudManager;
import com.claudemc.module.impl.misc.NoPacketKick;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void claudemc$onTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        HudManager.onWorldTimeUpdate();
    }
}
