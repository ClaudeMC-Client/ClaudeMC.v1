package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * AnchorAura — automatically detonates Respawn Anchors near enemy players.
 * Adapted from Wurst's AnchorAuraHack.
 *
 * Note: In the Overworld/End, Respawn Anchors explode when activated.
 * This module: finds nearby anchors, charges them (if holding glowstone),
 * then detonates them (right-click without glowstone) targeting enemies.
 */
public class AnchorAura extends Module {

    public static AnchorAura INSTANCE;

    private final NumberSetting rangeSetting;
    private final BoolSetting   autoChargeSetting;
    private final BoolSetting   targetPlayersSetting;

    public AnchorAura() {
        super("AnchorAura", "Detonates Respawn Anchors near enemies", Category.COMBAT);
        INSTANCE = this;
        rangeSetting         = addNumber("Range",         6.0, 1.0, 6.0, 0.5, false);
        autoChargeSetting    = addBool("AutoCharge",      true);
        targetPlayersSetting = addBool("TargetPlayers",   true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        double range = rangeSetting.get();

        // Find at least one enemy player in range to justify detonating anchors
        if (targetPlayersSetting.get() && !hasNearbyTarget(client, range + 2)) return;

        BlockPos playerPos = client.player.getBlockPos();
        int ri = (int) Math.ceil(range);

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    Vec3d center = Vec3d.ofCenter(pos);
                    if (client.player.getEyePos().distanceTo(center) > range) continue;

                    var state = client.world.getBlockState(pos);
                    if (!(state.getBlock() instanceof RespawnAnchorBlock)) continue;

                    int charge = state.get(RespawnAnchorBlock.CHARGES);

                    if (charge == 0) {
                        // Try to charge with glowstone if player is holding it
                        if (autoChargeSetting.get() && client.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                            interactBlock(client, pos);
                        }
                        continue;
                    }

                    // Detonation: right-click the anchor when not holding glowstone
                    if (!client.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                        interactBlock(client, pos);
                        return;
                    }
                }
            }
        }
    }

    private boolean hasNearbyTarget(MinecraftClient client, double range) {
        double rangeSq = range * range;
        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof PlayerEntity)) continue;
            if (!((LivingEntity) e).isAlive()) continue;
            if (e.squaredDistanceTo(client.player) <= rangeSq) return true;
        }
        return false;
    }

    private void interactBlock(MinecraftClient client, BlockPos pos) {
        Vec3d hitVec = Vec3d.ofCenter(pos);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        client.player.swingHand(Hand.MAIN_HAND);
    }
}
