package com.claudemc.mixin;

import com.claudemc.module.impl.player.NoBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoBackground: cancel the semi-transparent dirt/dark background behind GUI screens.
 */
@Mixin(Screen.class)
public class NoBackgroundMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (NoBackground.INSTANCE != null && NoBackground.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noInGameBackground(DrawContext context, CallbackInfo ci) {
        if (NoBackground.INSTANCE != null && NoBackground.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }
}
