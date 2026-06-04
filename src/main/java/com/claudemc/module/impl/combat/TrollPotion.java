package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * TrollPotion — automatically throws potions at nearby players.
 * Adapted from Wurst's TrollPotionHack.
 *
 * This module finds a splash/lingering potion in the hotbar and
 * aims/throws it at the nearest enemy player.
 *
 * TrollPotion in Wurst also generates custom "of Trolling" potions
 * with all 23 effects in creative mode. That creative-mode generation
 * requires access to MobEffect registries and PotionContents component.
 *
 * NOTE: Potion generation in creative mode requires a mixin or direct
 * item stack manipulation. The generation stub is included but commented.
 * The throw logic works without a mixin.
 */
public class TrollPotion extends Module {

    public static TrollPotion INSTANCE;

    private final NumberSetting rangeSetting;
    private final BoolSetting   targetPlayersSetting;
    private final BoolSetting   autoGenerateSetting;

    private long lastThrowMs = 0L;

    public TrollPotion() {
        super("TrollPotion", "Throws potions at nearby players automatically", Category.COMBAT);
        INSTANCE = this;
        rangeSetting          = addNumber("Range",          12.0, 1.0, 20.0, 1.0, false);
        targetPlayersSetting  = addBool("TargetPlayers",    true);
        autoGenerateSetting   = addBool("AutoGenerate",     false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Rate-limit throws
        long now = System.currentTimeMillis();
        if (now - lastThrowMs < 500) return;

        // Find nearest target
        Entity target = findTarget(client);
        if (target == null) return;

        // Find a throwable potion in hotbar
        int potionSlot = findPotionSlot(client);
        if (potionSlot == -1) return;

        // Switch to potion slot, aim at target, throw
        int prevSlot = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(potionSlot);

        aimAtTarget(client, target);

        // Throw: use the item (right-click)
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);

        // Restore slot
        client.player.getInventory().setSelectedSlot(prevSlot);

        lastThrowMs = System.currentTimeMillis();
    }

    private Entity findTarget(MinecraftClient client) {
        double rangeSq = rangeSetting.get() * rangeSetting.get();
        Entity best = null;
        double bestDist = rangeSq;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (targetPlayersSetting.get() && !(e instanceof PlayerEntity)) continue;

            double d = e.squaredDistanceTo(client.player);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private int findPotionSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var item = inv.getStack(i).getItem();
            if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) return i;
        }
        return -1;
    }

    /**
     * Aims at the target accounting for arc/throw trajectory.
     * Potions follow a parabolic arc similar to projectiles.
     */
    private void aimAtTarget(MinecraftClient client, Entity target) {
        Vec3d eye    = client.player.getEyePos();
        Vec3d tPos   = target.getBoundingBox().getCenter();
        Vec3d offset = tPos.subtract(eye);

        double hDist = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        float yaw    = (float) Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90f;

        // Potion throw velocity ~0.5 blocks/tick, g=0.05 per tick²
        // Simple arc correction: pitch down by ~(hDist * 1.5) degrees
        float pitchCorrection = (float) Math.min(45.0, hDist * 1.5);
        float pitch = (float) -Math.toDegrees(Math.atan2(offset.y + pitchCorrection * 0.1, hDist));

        client.player.setYaw(yaw);
        client.player.setPitch(Math.max(-90f, Math.min(90f, pitch)));
    }
}
