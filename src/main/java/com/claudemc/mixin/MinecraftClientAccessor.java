package com.claudemc.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the private final {@code session} field on {@link MinecraftClient}.
 *
 * Used by the AltManager to swap the active session at runtime. A mixin accessor is
 * remapped correctly in production (intermediary) builds, unlike a literal
 * {@code getDeclaredField("session")} which only works with yarn-named fields in dev.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Mutable
    @Accessor("session")
    void claudemc$setSession(Session session);
}
