package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoCrystal extends Module {

    private int placeCooldown = 0;

    public AutoCrystal() {
        super("AutoCrystal", "Auto-places and explodes end crystals on nearby players", Category.COMBAT);
        addNumber("Range",       4.0, 1.0, 10.0, 0.5, false);
        addNumber("MinDamage",   6.0, 1.0, 20.0, 1.0, false);
        addBool("AutoSwitch",    true);
        addBool("AntiSuicide",   true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        double range     = parseDouble(getSetting("Range"), 4.0);
        double minDmg    = parseDouble(getSetting("MinDamage"), 6.0);
        boolean autoSwap = Boolean.parseBoolean(getSetting("AutoSwitch"));

        // 1. Explode existing crystals
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity crystal)) continue;
            if (crystal.distanceTo(client.player) > range + 2) continue;

            double dmg = estimateCrystalDamage(client, crystal.getEntityPos());
            if (dmg < minDmg) continue;
            if (Boolean.parseBoolean(getSetting("AntiSuicide")) &&
                estimateSelfDamage(client, crystal.getEntityPos()) > 8.0) continue;

            client.interactionManager.attackEntity(client.player, crystal);
            client.player.swingHand(Hand.MAIN_HAND);
            return;
        }

        // 2. Place crystal on obsidian/bedrock near target
        if (--placeCooldown > 0) return;
        placeCooldown = 3;

        PlayerEntity target = findTarget(client, range);
        if (target == null) return;

        BlockPos placePos = findPlacePos(client, target, (int) range);
        if (placePos == null) return;

        int crystalSlot = findCrystalSlot(client);
        if (crystalSlot == -1) return;

        int prev = client.player.getInventory().getSelectedSlot();
        if (autoSwap) client.player.getInventory().setSelectedSlot(crystalSlot);

        client.interactionManager.interactBlock(client.player,
            Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(placePos).add(0, 0.5, 0), Direction.UP, placePos, false));

        if (autoSwap) client.player.getInventory().setSelectedSlot(prev);
    }

    private PlayerEntity findTarget(MinecraftClient client, double range) {
        PlayerEntity best = null;
        double bestDist = range * range;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity p) || e == client.player) continue;
            if (!p.isAlive()) continue;
            double d = p.squaredDistanceTo(client.player);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private BlockPos findPlacePos(MinecraftClient client, PlayerEntity target, int radius) {
        var reg = net.minecraft.registry.Registries.BLOCK;
        for (BlockPos pos : BlockPos.iterateOutwards(target.getBlockPos(), radius, 1, radius)) {
            var state = client.world.getBlockState(pos);
            String id = reg.getId(state.getBlock()).toString();
            if (!"minecraft:obsidian".equals(id) && !"minecraft:bedrock".equals(id)) continue;
            BlockPos top = pos.up();
            if (!client.world.getBlockState(top).isAir()) continue;
            if (client.player.squaredDistanceTo(Vec3d.ofCenter(top)) > 36) continue;
            return top;
        }
        return null;
    }

    private int findCrystalSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Items.END_CRYSTAL) return i;
        }
        return -1;
    }

    private double estimateCrystalDamage(MinecraftClient client, Vec3d crystalPos) {
        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity p) || e == client.player) continue;
            double d = p.squaredDistanceTo(crystalPos);
            if (d < nearestDist) { nearestDist = d; nearest = p; }
        }
        if (nearest == null) return 0;
        double dist = Math.sqrt(nearestDist);
        return Math.max(0, 12.0 - dist * 2);
    }

    private double estimateSelfDamage(MinecraftClient client, Vec3d crystalPos) {
        double dist = client.player.getEntityPos().distanceTo(crystalPos);
        return Math.max(0, 12.0 - dist * 2);
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}
