package com.claudemc.mixin;

import com.claudemc.companion.CompanionServer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Shadow public int width;

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    @Inject(method = "init", at = @At("TAIL"))
    private void claudemc$addCompanionButton(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§a[§fClaudeMC§a]"),
                btn -> CompanionServer.INSTANCE.open()
        ).dimensions(width - 114, 4, 110, 20).build());
    }
}
