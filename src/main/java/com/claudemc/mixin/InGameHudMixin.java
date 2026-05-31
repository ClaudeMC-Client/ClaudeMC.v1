package com.claudemc.mixin;

import com.claudemc.module.impl.render.NoRender;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noFireOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noFire()) ci.cancel();
    }

    @Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noBossBar(DrawContext context, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noBossBar()) ci.cancel();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noPotionHUD(DrawContext context, float f, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noPotionHUD()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noScoreboard(DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noScoreboard()) ci.cancel();
    }
}
