package com.claudemc.mixin;

import com.claudemc.module.impl.player.NoHurtcam;
import com.claudemc.module.impl.render.Fullbright;
import com.claudemc.module.impl.render.Zoom;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /** Zoom: divide the calculated FOV by the zoom factor while Zoom is active.
     *  1.21.x getFov returns float (was double), so the CIR is parameterised with Float. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void claudemc$zoom(net.minecraft.client.render.Camera camera,
                                float tickDelta,
                                boolean changingFov,
                                CallbackInfoReturnable<Float> cir) {
        if (Zoom.INSTANCE != null && Zoom.INSTANCE.isEnabled()) {
            float fov = cir.getReturnValue();
            cir.setReturnValue((float) (fov / Zoom.INSTANCE.getZoomFactor()));
        }
    }

    /** NoHurtcam: cancel the view-tilt that plays when taking damage. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void claudemc$noHurtcam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoHurtcam.INSTANCE != null && NoHurtcam.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }
}
