package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class Reach extends Module {

    public static Reach INSTANCE;

    public Reach() {
        super("Reach", "Extends melee attack and block interaction range", Category.COMBAT);
        addNumber("AttackReach",  5.0, 3.0, 10.0, 0.5, false);
        addNumber("BlockReach",   5.0, 3.0, 10.0, 0.5, false);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        double attackRange = parseDouble(getSetting("AttackReach"), 5.0);
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f);

        Entity best = null;
        double bestDot = 0.97;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double dist = e.distanceTo(client.player);
            if (dist > attackRange) continue;

            Vec3d toEntity = e.getEntityPos().subtract(eye).normalize();
            double dot = look.dotProduct(toEntity);
            if (dot > bestDot) { bestDot = dot; best = e; }
        }

        if (best != null && client.options.attackKey.isPressed()) {
            client.interactionManager.attackEntity(client.player, best);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    public double getAttackReach() {
        return parseDouble(getSetting("AttackReach"), 5.0);
    }

    public double getBlockReach() {
        return parseDouble(getSetting("BlockReach"), 5.0);
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}
