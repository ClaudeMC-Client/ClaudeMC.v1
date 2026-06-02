package com.claudemc.mixin;

import com.claudemc.companion.CompanionServer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Extends Screen so addDrawableChild and width are accessible without @Shadow.
// @Shadow on members declared in a parent class fails when no refMap is loaded.
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() { super(Text.empty()); }

    // URL label shown after click — blank initially
    private String companionUrl = "";

    @Inject(method = "init", at = @At("TAIL"))
    private void claudemc$addCompanionButton(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§a[§fClaudeMC§a] Companion"),
                btn -> {
                    CompanionServer.INSTANCE.open();
                    companionUrl = CompanionServer.INSTANCE.getUrl();
                }
        ).dimensions(width - 184, 4, 180, 20).build());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void claudemc$renderUrl(net.minecraft.client.gui.DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (companionUrl.isEmpty()) return;
        String label = "§7Companion: §f" + companionUrl + " §8(copied to clipboard)";
        int tw = textRenderer.getWidth(label.replaceAll("§.", ""));
        ctx.fill(width/2 - tw/2 - 3, height - 24, width/2 + tw/2 + 3, height - 10, 0xCC000000);
        ctx.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal(label), width/2, height - 21, 0xFFFFFF);
    }
}
