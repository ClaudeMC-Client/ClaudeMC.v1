package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

public class ForcePush extends Module {

    public static ForcePush INSTANCE;

    public ForcePush() {
        super("ForcePush", "Pushes nearby entities away by simulating velocity changes", Category.EXPLOIT);
        INSTANCE = this;
        addNumber("Radius", 5, 1, 20, 1, true);
        addNumber("Strength", 2, 1, 10, 1, true);
        addBool("PushAll", true);
        addSetting("Target", "");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        double radius = parseDouble(getSetting("Radius"), 5);
        double strength = parseDouble(getSetting("Strength"), 2);
        boolean pushAll = Boolean.parseBoolean(getSetting("PushAll"));
        String targetName = getSetting("Target").toLowerCase();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!pushAll) {
                if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
                if (!player.getName().getString().toLowerCase().contains(targetName)) continue;
            }

            double dist = entity.distanceTo(mc.player);
            if (dist > radius || dist < 0.1) continue;

            // Calculate push direction (away from player)
            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.01) continue;

            dx /= len;
            dz /= len;

            // Apply velocity on client side (cosmetic/local only without server packets)
            entity.setVelocity(dx * strength, entity.getVelocity().y + 0.2, dz * strength);
        }
    }

    private double parseDouble(String s, double d) {
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
