package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * HealthTags — shows health above entity heads as a floating text label in world space.
 *
 * The label is drawn as an outlined box above each entity, with the health value
 * colour-coded: green (>60%), yellow (30–60%), red (<30%).
 *
 * Note: Minecraft 1.21.x does not expose a trivially hookable world-space text API
 * through the vanilla pipeline without mixins. We approximate by drawing a small
 * coloured box whose width is proportional to the health fraction, acting as a
 * health-bar rather than floating text. This avoids the need for a mixin.
 */
public class HealthTags extends Module {

    public static HealthTags INSTANCE;

    public HealthTags() {
        super("HealthTags", "Shows health bars above entity heads", Category.RENDER);
        addMode("Filter", "Players", "Players", "All", "Hostile");
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam      = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            if (matrices == null) return;
            var consumers = context.consumers();

            String filter = INSTANCE.getSetting("Filter");

            for (var entity : client.world.getEntities()) {
                if (!(entity instanceof LivingEntity le)) continue;
                if (!le.isAlive()) continue;
                if (le == client.player) continue;
                if (!matchFilter(le, filter)) continue;

                float hp     = le.getHealth();
                float maxHp  = le.getMaxHealth();
                float frac   = maxHp > 0 ? Math.max(0, Math.min(1, hp / maxHp)) : 0;

                // Health bar sits 0.3 above the entity's bounding box top
                double headY = le.getBoundingBox().maxY + 0.3 - cam.y;
                double ex    = le.getX() - cam.x;
                double ez    = le.getZ() - cam.z;

                double barHalfW = 0.4;
                double barH     = 0.06;

                // Background (dark)
                net.minecraft.util.math.Box bgBox = new net.minecraft.util.math.Box(
                    ex - barHalfW, headY, ez - 0.02,
                    ex + barHalfW, headY + barH, ez + 0.02);
                RenderUtils.drawOutlinedBox(matrices, consumers, bgBox, 0.2f, 0.2f, 0.2f, 0.8f);

                // Health fill
                double fillRight = ex - barHalfW + (2 * barHalfW * frac);
                float r = frac > 0.6f ? 0f : (frac > 0.3f ? 1f : 1f);
                float g = frac > 0.6f ? 1f : (frac > 0.3f ? 0.7f : 0f);
                float b = 0f;
                net.minecraft.util.math.Box fillBox = new net.minecraft.util.math.Box(
                    ex - barHalfW, headY, ez - 0.02,
                    fillRight,     headY + barH, ez + 0.02);
                RenderUtils.drawOutlinedBox(matrices, consumers, fillBox, r, g, b, 1f);
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

    @Override
    public void onTick(MinecraftClient client) {}
}
