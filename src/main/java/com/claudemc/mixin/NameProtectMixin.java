package com.claudemc.mixin;

import com.claudemc.module.impl.player.NameProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NameProtect: replaces the player's real username with a fake name in chat messages.
 */
@Mixin(ChatHud.class)
public class NameProtectMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), argsOnly = true, require = 0
    )
    private Text claudemc$nameProtect(Text original) {
        if (NameProtect.INSTANCE == null || !NameProtect.INSTANCE.isEnabled()) return original;
        var client = MinecraftClient.getInstance();
        if (client.player == null) return original;

        String realName = client.player.getName().getString();
        String raw      = original.getString();
        if (!raw.contains(realName)) return original;

        // Replace occurrences of the real name in the serialised string
        String replaced = raw.replace(realName, NameProtect.INSTANCE.getReplacement());
        return Text.literal(replaced);
    }
}
