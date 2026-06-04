package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Renders invisible entities by drawing a semi-transparent outlined box
 * around them. Without this module invisible entities are not rendered by
 * Minecraft's normal pipeline.
 */
public class TrueSight extends Module {

    public static TrueSight INSTANCE;

    public TrueSight() {
        super("TrueSight", "Reveals invisible entities with a translucent outline", Category.RENDER);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam      = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();

            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof LivingEntity le)) continue;
                if (!le.isInvisible()) continue;
                if (!le.isAlive()) continue;

                float r, g, b;
                if (le instanceof PlayerEntity) {
                    r = 1f; g = 0.2f; b = 0.2f;
                } else {
                    r = 0.8f; g = 0.8f; b = 0.8f;
                }

                Box box = le.getBoundingBox().expand(0.05).offset(-cam.x, -cam.y, -cam.z);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, r, g, b, 0.6f);
            }
        });
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
