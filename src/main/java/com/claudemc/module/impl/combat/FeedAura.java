package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;

/**
 * FeedAura — automatically feeds/breeds nearby animals.
 * Adapted from Wurst's FeedAuraHack.
 *
 * Finds animals that are interested in food (in love mode or can fall in love),
 * then right-clicks them with the held food item.
 */
public class FeedAura extends Module {

    public static FeedAura INSTANCE;

    private final NumberSetting rangeSetting;
    private final BoolSetting   filterBabiesSetting;
    private final BoolSetting   filterUntamedSetting;
    private final BoolSetting   filterHorsesSetting;

    private long lastFeedMs = 0L;

    public FeedAura() {
        super("FeedAura", "Automatically feeds and breeds nearby animals", Category.COMBAT);
        INSTANCE = this;
        rangeSetting         = addNumber("Range",          5.0, 1.0, 10.0, 0.5, false);
        filterBabiesSetting  = addBool("FilterBabies",     true);
        filterUntamedSetting = addBool("FilterUntamed",    false);
        filterHorsesSetting  = addBool("FilterHorses",     false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Throttle to avoid spam
        long now = System.currentTimeMillis();
        if (now - lastFeedMs < 250) return;

        ItemStack heldStack = client.player.getMainHandStack();
        if (heldStack.isEmpty()) return;

        double range   = rangeSetting.get();
        double rangeSq = range * range;

        AnimalEntity target = null;
        double bestDist = rangeSq;

        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof AnimalEntity animal)) continue;
            if (!animal.isAlive()) continue;
            if (!animal.isBreedingItem(heldStack)) continue;
            if (!animal.canEat()) continue;

            // Filter babies
            if (filterBabiesSetting.get() && animal.isBaby()) continue;

            // Filter untamed
            if (filterUntamedSetting.get() && isUntamed(animal)) continue;

            // Filter horses
            if (filterHorsesSetting.get() && animal instanceof AbstractHorseEntity) continue;

            double d = animal.squaredDistanceTo(client.player);
            if (d < bestDist) { bestDist = d; target = animal; }
        }

        if (target == null) return;

        // Face the animal
        faceEntity(client, target);

        // Right-click the animal to feed it
        Box box = target.getBoundingBox();
        Vec3d eyePos = client.player.getEyePos();
        Vec3d center = box.getCenter();
        Vec3d hitVec = box.raycast(eyePos, center).orElse(center);

        EntityHitResult hitResult = new EntityHitResult(target, hitVec);
        client.interactionManager.interactEntityAtLocation(
                client.player, target, hitResult, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);

        lastFeedMs = System.currentTimeMillis();
    }

    private boolean isUntamed(AnimalEntity e) {
        if (e instanceof AbstractHorseEntity horse && !horse.isTame()) return true;
        if (e instanceof TameableEntity tame && !tame.isTamed()) return true;
        return false;
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
