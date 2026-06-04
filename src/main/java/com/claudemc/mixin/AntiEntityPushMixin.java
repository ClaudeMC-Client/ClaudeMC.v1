package com.claudemc.mixin;

import com.claudemc.module.impl.movement.AntiEntityPush;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class AntiEntityPushMixin {

    /** AntiEntityPush: cancel pushAwayFrom so entities can't push the player */
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$antiEntityPush(Entity other, CallbackInfo ci) {
        if (AntiEntityPush.INSTANCE == null || !AntiEntityPush.INSTANCE.isEnabled()) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this == mc.player) {
            ci.cancel();
        }
    }
}
