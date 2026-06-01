package com.claudemc.mixin;

import com.claudemc.module.impl.render.NoRender;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
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

    // Boss bars render in BossBarHud (not InGameHud) on 1.21.1; require=0 keeps this a no-op.
    @Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noBossBar(DrawContext context, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noBossBar()) ci.cancel();
    }

    // renderStatusEffectOverlay takes a RenderTickCounter (the handler previously declared a float,
    // which is a fatal injection-descriptor mismatch once InGameHud actually loads).
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noPotionHUD(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noPotionHUD()) ci.cancel();
    }

    // Two renderScoreboardSidebar overloads exist — target the (DrawContext, ScoreboardObjective)
    // renderer explicitly so Mixin doesn't ambiguously match the RenderTickCounter overload.
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noScoreboard(DrawContext context, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.noScoreboard()) ci.cancel();
    }
}
