package com.claudemc.module.impl;

import com.claudemc.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class EspModule extends Module {

    public enum EspFilter { ALL, PLAYERS_ONLY, HOSTILES_ONLY }

    private EspFilter filter = EspFilter.ALL;

    private static EspModule INSTANCE;

    public EspModule() {
        super("ESP", "Renders coloured outlines around entities through walls", "Visual");
        INSTANCE = this;

        // Registered once; guarded by isEnabled() at runtime
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;

            Vec3d cam = context.camera().getPos();

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;
            VertexConsumer lines = consumers.getBuffer(RenderLayer.LINES);

            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!shouldRender(entity)) continue;

                float[] col = colorFor(entity);
                Box box = entity.getBoundingBox().expand(0.05);
                Box relative = box.offset(-cam.x, -cam.y, -cam.z);

                matrices.push();
                WorldRenderer.drawBox(matrices, lines, relative, col[0], col[1], col[2], 1.0f);
                matrices.pop();
            }

            if (consumers instanceof VertexConsumerProvider.Immediate imm) {
                imm.draw(RenderLayer.LINES);
            }
        });
    }

    private boolean shouldRender(Entity entity) {
        return switch (filter) {
            case PLAYERS_ONLY  -> entity instanceof PlayerEntity;
            case HOSTILES_ONLY -> entity instanceof HostileEntity;
            case ALL           -> entity instanceof LivingEntity;
        };
    }

    private float[] colorFor(Entity entity) {
        if (entity instanceof PlayerEntity)   return new float[]{1.0f, 0.25f, 0.25f}; // red
        if (entity instanceof HostileEntity)  return new float[]{1.0f, 0.65f, 0.0f};  // orange
        return new float[]{0.25f, 1.0f, 0.25f};                                        // green
    }

    @Override public void onTick(MinecraftClient client) {}

    public EspFilter getFilter()              { return filter; }
    public void setFilter(EspFilter filter)   { this.filter = filter; }
}
