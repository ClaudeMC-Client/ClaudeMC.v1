package com.claudemc.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code boundKey} field on {@link KeyBinding} so movement overrides
 * (InventoryMove/AutoWalk) poll the key the user actually bound, not the hard-coded default.
 */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {

    @Accessor("boundKey")
    InputUtil.Key claudemc$getBoundKey();
}
