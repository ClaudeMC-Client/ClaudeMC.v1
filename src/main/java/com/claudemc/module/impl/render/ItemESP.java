package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;

public class ItemESP extends Module {

    public static ItemESP INSTANCE;

    public ItemESP() {
        super("ItemESP", "Highlights dropped item entities through walls", Category.RENDER);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam      = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();

            for (var entity : client.world.getEntities()) {
                if (!(entity instanceof ItemEntity ie)) continue;
                Box box = ie.getBoundingBox().expand(0.1).offset(-cam.x, -cam.y, -cam.z);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, 1f, 1f, 0f, 1f);
            }
        });
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
