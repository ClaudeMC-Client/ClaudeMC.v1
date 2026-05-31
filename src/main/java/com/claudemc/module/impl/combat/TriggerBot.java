package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class TriggerBot extends Module {

    private int attackDelay = 0;

    public TriggerBot() {
        super("TriggerBot", "Attacks automatically when your crosshair is on a valid target", Category.COMBAT);
        addMode("Target", "Players", "Players", "Hostile+Players", "Hostile", "All");
        addNumber("Delay", 2.0, 0.0, 20.0, 1.0, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        if (--attackDelay > 0) return;

        if (!(client.crosshairTarget instanceof EntityHitResult ehr)) return;
        if (ehr.getType() != HitResult.Type.ENTITY) return;

        Entity e = ehr.getEntity();
        if (!(e instanceof LivingEntity le)) return;
        if (!le.isAlive()) return;
        if (!shouldTarget(le, getSetting("Target"))) return;

        attackDelay = parseInt(getSetting("Delay"), 2);
        client.interactionManager.attackEntity(client.player, e);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean shouldTarget(LivingEntity e, String mode) {
        return switch (mode) {
            case "Players"         -> e instanceof PlayerEntity;
            case "Hostile"         -> e instanceof HostileEntity;
            case "Hostile+Players" -> e instanceof HostileEntity || e instanceof PlayerEntity;
            default                -> true;
        };
    }

    @Override
    public void onDisable() {
        attackDelay = 0;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return def; }
    }
}
