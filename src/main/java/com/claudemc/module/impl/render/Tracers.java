package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Tracers extends Module {

    public static Tracers INSTANCE;

    public Tracers() {
        super("Tracers", "Draw lines from crosshair to entities", Category.RENDER);
        addMode("Filter", "Players", "Players", "Hostile", "All");
        addNumber("Range", 64, 8, 256, 4, true);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            String filter = INSTANCE.getSetting("Filter");
            double range  = parseDouble(INSTANCE.getSetting("Range"), 64);

            Vec3d origin = Vec3d.ZERO; // relative to camera in world-space render

            for (Entity e : client.world.getEntities()) {
                if (e == client.player) continue;
                if (!(e instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                if (e.distanceTo(client.player) > range) continue;
                if (!matchFilter(le, filter)) continue;

                float[] col = colorFor(le);
                Vec3d target = le.getBoundingBox().getCenter().subtract(cam);
                RenderUtils.drawLine(matrices, consumers, origin, target, col[0], col[1], col[2], 0.8f);
            }
        });
    }

    private boolean matchFilter(LivingEntity e, String f) {
        return switch (f) {
            case "Players" -> e instanceof PlayerEntity;
            case "Hostile" -> e instanceof HostileEntity;
            default        -> true;
        };
    }

    private float[] colorFor(LivingEntity e) {
        if (e instanceof PlayerEntity)  return new float[]{1f, 0.2f, 0.2f};
        if (e instanceof HostileEntity) return new float[]{1f, 0.6f, 0f};
        return new float[]{0.2f, 1f, 0.2f};
    }

    private static double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }

    @Override public void onTick(MinecraftClient client) {}
}
