package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Renders player/mob bounding boxes through walls with a filled/glowing tint.
 * Uses the same outlined-box approach as ESP but with higher alpha.
 */
public class Chams extends Module {

    public static Chams INSTANCE;

    public Chams() {
        super("Chams", "Renders entity hitboxes through walls with solid colour fill", Category.RENDER);
        addMode("Filter", "All", "All", "Players", "Hostile");
        addNumber("Alpha", 0.6, 0.1, 1.0, 0.05, false);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam      = context.camera().getPos();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            String filter = INSTANCE.getSetting("Filter");
            float alpha   = (float) parseDouble(INSTANCE.getSetting("Alpha"), 0.6);

            for (Entity e : client.world.getEntities()) {
                if (e == client.player) continue;
                if (!(e instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                if (!match(le, filter)) continue;

                float[] c = color(le);
                Box box = le.getBoundingBox().expand(0.02).offset(-cam.x, -cam.y, -cam.z);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, c[0], c[1], c[2], alpha);
            }
        });
    }

    private boolean match(LivingEntity e, String f) {
        return switch (f) {
            case "Players" -> e instanceof PlayerEntity;
            case "Hostile" -> e instanceof HostileEntity;
            default        -> true;
        };
    }

    private float[] color(LivingEntity e) {
        if (e instanceof PlayerEntity)  return new float[]{1f, 0.4f, 0.4f};
        if (e instanceof HostileEntity) return new float[]{1f, 0.7f, 0.1f};
        return new float[]{0.4f, 1f, 0.4f};
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    @Override public void onTick(MinecraftClient client) {}
}
