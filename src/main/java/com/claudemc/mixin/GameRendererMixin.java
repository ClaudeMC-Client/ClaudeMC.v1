package com.claudemc.mixin;

import com.claudemc.module.impl.render.Fullbright;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /** Fullbright: return maximum sky-darkener value so lighting is ignored */
    @Inject(method = "getNightVisionStrength", at = @At("RETURN"), cancellable = true)
    private static void claudemc$fullbright(net.minecraft.entity.LivingEntity entity,
                                             float tickProgress,
                                             CallbackInfoReturnable<Float> cir) {
        if (Fullbright.INSTANCE != null && Fullbright.INSTANCE.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }
}
