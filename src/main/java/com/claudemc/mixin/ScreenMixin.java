package com.claudemc.mixin;

import com.claudemc.chat.ChatOverlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts key/char events on any open Screen so the ChatOverlay
 * can capture input while a GUI (auction house, chest, etc.) is open.
 *
 * 1.21.x changed Screen.keyPressed to take a {@link KeyInput} record.
 */
@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void claudemc$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (ChatOverlay.INSTANCE.isActive()) {
            boolean consumed = ChatOverlay.INSTANCE.keyPressed(input.key(), net.minecraft.client.MinecraftClient.getInstance());
            if (consumed) { cir.setReturnValue(true); cir.cancel(); }
        }
    }

}
