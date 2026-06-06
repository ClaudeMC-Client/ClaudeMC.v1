package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Extends melee and block interaction reach via EntityAttributeInstance.setBaseValue.
 * ENTITY_INTERACTION_RANGE default = 3.0, BLOCK_INTERACTION_RANGE default = 4.5.
 * The game uses these attributes natively for all raycasting, so no manual entity
 * loop is needed — just set and let vanilla handle the rest.
 */
public class Reach extends Module {

    public static Reach INSTANCE;

    private static final double DEFAULT_ENTITY = 3.0;
    private static final double DEFAULT_BLOCK   = 4.5;

    public Reach() {
        super("Reach", "Extends melee attack and block interaction range", Category.COMBAT);
        addNumber("AttackReach", 5.0, 3.0, 10.0, 0.5, false);
        addNumber("BlockReach",  5.0, 3.0, 10.0, 0.5, false);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        apply(MinecraftClient.getInstance());
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        var ea = c.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        var ba = c.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        if (ea != null) ea.setBaseValue(DEFAULT_ENTITY);
        if (ba != null) ba.setBaseValue(DEFAULT_BLOCK);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        apply(client);
    }

    private void apply(MinecraftClient client) {
        if (client.player == null) return;
        double attackReach = parseDouble(getSetting("AttackReach"), 3.0);
        double blockReach  = parseDouble(getSetting("BlockReach"), 4.5);
        var ea = client.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        var ba = client.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        if (ea != null) ea.setBaseValue(attackReach);
        if (ba != null) ba.setBaseValue(blockReach);
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
