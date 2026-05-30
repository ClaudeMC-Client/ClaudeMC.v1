package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class StorageESP extends Module {

    public static StorageESP INSTANCE;

    public StorageESP() {
        super("StorageESP", "Shows container contents indicator through walls", Category.RENDER);
        addSetting("Radius", "32");
        addSetting("ShowFull",  "true");
        addSetting("ShowEmpty", "false");
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam = context.camera().getPos();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var consumers = context.consumers();
            if (consumers == null) return;

            int radius = Integer.parseInt(INSTANCE.getSetting("Radius"));
            var pPos = client.player.getBlockPos();

            for (BlockPos pos : BlockPos.iterateOutwards(pPos, radius, radius, radius)) {
                var be = client.world.getBlockEntity(pos);
                if (!isContainer(be)) continue;

                double bx = pos.getX() - cam.x;
                double by = pos.getY() - cam.y;
                double bz = pos.getZ() - cam.z;

                float fill = getFill(be);
                // Green = items present, red = empty, yellow = partially full
                float r = fill < 0.1f ? 1f : (fill < 0.9f ? 1f : 0f);
                float g = fill < 0.1f ? 0f : (fill < 0.9f ? 0.8f : 1f);
                float b = 0f;

                Box box = new Box(bx + 0.05, by + 0.05, bz + 0.05, bx + 0.95, by + 0.95, bz + 0.95);
                RenderUtils.drawOutlinedBox(matrices, consumers, box, r, g, b, 1f);
            }
        });
    }

    private boolean isContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity
            || be instanceof BarrelBlockEntity || be instanceof HopperBlockEntity
            || be instanceof FurnaceBlockEntity || be instanceof DispenserBlockEntity;
    }

    private float getFill(BlockEntity be) {
        if (be instanceof ChestBlockEntity c) {
            int used = 0;
            for (int i = 0; i < c.size(); i++) if (!c.getStack(i).isEmpty()) used++;
            return (float) used / c.size();
        }
        if (be instanceof ShulkerBoxBlockEntity s) {
            int used = 0;
            for (int i = 0; i < s.size(); i++) if (!s.getStack(i).isEmpty()) used++;
            return (float) used / s.size();
        }
        return 0.5f;
    }

    @Override public void onTick(MinecraftClient client) {}
}
