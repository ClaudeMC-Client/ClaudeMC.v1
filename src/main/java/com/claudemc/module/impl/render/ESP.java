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

public class ESP extends Module {

    public static ESP INSTANCE;

    public ESP() {
        super("ESP", "Draw coloured boxes around entities through walls", Category.RENDER);
        addMode("Filter", "All", "All", "Players", "Hostile");
        addSetting("ShowInvis", "true");
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam = context.camera().getPos();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var consumers = context.consumers();

            String filter  = INSTANCE.getSetting("Filter");

            for (Entity e : client.world.getEntities()) {
                if (e == client.player) continue;
                if (!(e instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                if (!matchFilter(le, filter)) continue;

                float[] col = colorFor(le);
                Box box = le.getBoundingBox().expand(0.05).offset(-cam.x, -cam.y, -cam.z);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, col[0], col[1], col[2], 1f);
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

    @Override public void onTick(MinecraftClient client) {}
}
