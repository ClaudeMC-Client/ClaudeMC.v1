package com.claudemc.mixin;

import com.claudemc.module.impl.render.Fullbright;
import com.claudemc.module.impl.render.Zoom;
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

    /** Zoom: divide the calculated FOV by the zoom factor while Zoom is active. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void claudemc$zoom(net.minecraft.client.render.Camera camera,
                                float tickDelta,
                                boolean changingFov,
                                CallbackInfoReturnable<Double> cir) {
        if (Zoom.INSTANCE != null && Zoom.INSTANCE.isEnabled()) {
            double fov = cir.getReturnValue();
            cir.setReturnValue(fov / Zoom.INSTANCE.getZoomFactor());
        }
    }
}
