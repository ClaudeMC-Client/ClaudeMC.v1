package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * BlockHit — automatically blocks with a shield/off-hand while attacking.
 * Simulates the sword+shield (block-hit) technique used in PvP.
 *
 * The technique: hold use (right-click) to raise shield/block, then attack.
 * This gives damage reduction while still dealing damage.
 *
 * NOTE: Actually triggering the right-click use action to raise a shield
 * needs a mixin to inject into the input handling. Without a mixin,
 * we can only swing the attack hand. The shield-raising stub is noted below.
 *
 * Core mixin hook: inject into ClientPlayerInteractionManager.attackEntity
 * to ensure the use action is active when attacking. (mixin to be added separately).
 */
public class BlockHit extends Module {

    public static BlockHit INSTANCE;

    private final BoolSetting onlyWithSwordSetting;
    private final BoolSetting requireShieldSetting;

    public BlockHit() {
        super("BlockHit", "Blocks with shield while attacking (sword+shield technique)", Category.COMBAT);
        INSTANCE = this;
        onlyWithSwordSetting = addBool("OnlyWithSword", true);
        requireShieldSetting = addBool("RequireShield", true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Check weapon requirement (SwordItem was removed in 1.21.x; check via known sword/axe items)
        if (onlyWithSwordSetting.get()) {
            var mainItem = client.player.getMainHandStack().getItem();
            boolean isSword = mainItem == Items.WOODEN_SWORD || mainItem == Items.STONE_SWORD
                    || mainItem == Items.IRON_SWORD || mainItem == Items.GOLDEN_SWORD
                    || mainItem == Items.DIAMOND_SWORD || mainItem == Items.NETHERITE_SWORD;
            boolean isAxe = mainItem instanceof AxeItem;
            if (!isSword && !isAxe) return;
        }

        // Check shield requirement
        if (requireShieldSetting.get()) {
            boolean hasShield = client.player.getMainHandStack().isOf(net.minecraft.item.Items.SHIELD)
                || client.player.getOffHandStack().isOf(net.minecraft.item.Items.SHIELD);
            if (!hasShield) return;
        }

        // The block-hit technique: simulate right-click (use) to raise shield
        // then attack on the same tick.
        // NOTE: Full implementation requires a mixin to inject before attackEntity
        // to press the use key. The below is the best we can do via onTick:
        performBlockHit(client);
    }

    private void performBlockHit(MinecraftClient client) {
        // Find nearest attackable entity
        Entity target = findTarget(client, 4.0);
        if (target == null) return;

        // Only attack if cooldown is ready
        if (client.player.getAttackCooldownProgress(0f) < 0.9f) return;

        // Raise shield: simulate using the off-hand item (shield)
        // This is the closest we can get without a mixin:
        // client.interactionManager.interactItem(client.player, Hand.OFF_HAND);

        // Attack the target
        faceEntity(client, target);
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private Entity findTarget(MinecraftClient client, double range) {
        double rangeSq = range * range;
        Entity best = null;
        double bestDist = rangeSq;
        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (!(e instanceof PlayerEntity || e instanceof HostileEntity)) continue;
            double d = e.squaredDistanceTo(client.player);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private void faceEntity(MinecraftClient client, Entity target) {
        var eye  = client.player.getEyePos();
        var tPos = target.getBoundingBox().getCenter();
        var d    = tPos.subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, h));
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }
}
