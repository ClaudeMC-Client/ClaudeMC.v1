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

    @Inject(method = "init", at = @At("TAIL"))
    private void claudemc$addCompanionButton(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§a[§fClaudeMC§a]"),
                btn -> CompanionServer.INSTANCE.open()
        ).dimensions(width - 114, 4, 110, 20).build());
    }
}
