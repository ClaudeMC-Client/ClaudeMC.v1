package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * PropHunt ESP — highlights players that may be disguised as props.
 * Detection heuristic: a player whose bounding box dimensions are unusually
 * small (width <= 0.6 and/or height <= 0.9) compared to the default player
 * hitbox (0.6 wide × 1.8 tall), OR whose bounding box is not centred on
 * their feet (suggesting an armour-stand / block-sized hitbox replacement).
 */
public class ProphuntESP extends Module {

    public static ProphuntESP INSTANCE;

    public ProphuntESP() {
        super("ProphuntESP", "Highlights players disguised as props in PropHunt servers", Category.RENDER);
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
                if (!(entity instanceof PlayerEntity player)) continue;
                if (player == client.player) continue;

                Box bb = player.getBoundingBox();
                double width  = bb.maxX - bb.minX;
                double height = bb.maxY - bb.minY;

                boolean likelyProp = width < 0.55 || height < 1.7 || height > 1.85;

                // Always show all players with a color indicating suspicion
                float r, g, b;
                if (likelyProp) {
                    r = 1f; g = 0f; b = 1f; // magenta = suspicious
                } else {
                    r = 1f; g = 1f; b = 0f; // yellow = normal player
                }

                Box drawBox = bb.expand(0.05).offset(-cam.x, -cam.y, -cam.z);
                RenderUtils.drawOutlinedBox(matrices, consumers, drawBox, r, g, b, 1f);
            }
        });
    }

    @Override
    public void onTick(MinecraftClient client) {}
}
