package com.claudemc.mixin;

import com.claudemc.module.impl.render.AntiInvis;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    /** AntiInvis: override isInvisible() so all entities always render */
    @Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
    private void claudemc$antiInvis(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && AntiInvis.INSTANCE != null && AntiInvis.INSTANCE.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
