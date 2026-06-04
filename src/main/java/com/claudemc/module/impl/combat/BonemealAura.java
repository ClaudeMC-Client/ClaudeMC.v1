package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * BonemealAura — automatically uses bonemeal on nearby crops/saplings.
 * Adapted from Wurst's BonemealAuraHack.
 *
 * Note: Wurst places this in Category.BLOCKS; we place it in COMBAT
 * as instructed (all new modules go to combat package/category).
 */
public class BonemealAura extends Module {

    public static BonemealAura INSTANCE;

    private final NumberSetting rangeSetting;
    private final BoolSetting   saplingsSetting;
    private final BoolSetting   cropsSetting;
    private final BoolSetting   stemsSetting;
    private final BoolSetting   cocoaSetting;
    private final BoolSetting   otherSetting;

    private long lastUseMs = 0L;

    public BonemealAura() {
        super("BonemealAura", "Automatically uses bonemeal on nearby crops and saplings", Category.COMBAT);
        INSTANCE = this;
        rangeSetting  = addNumber("Range",    5.0, 1.0, 6.0, 0.5, false);
        saplingsSetting = addBool("Saplings", true);
        cropsSetting    = addBool("Crops",    true);
        stemsSetting    = addBool("Stems",    true);
        cocoaSetting    = addBool("Cocoa",    true);
        otherSetting    = addBool("Other",    false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Must be holding bonemeal or have it in hotbar
        boolean holdingBoneMeal = client.player.getMainHandStack().isOf(Items.BONE_MEAL);
        int boneMealSlot = -1;

        if (!holdingBoneMeal) {
            var inv = client.player.getInventory();
            for (int i = 0; i < 9; i++) {
                if (inv.getStack(i).isOf(Items.BONE_MEAL)) {
                    boneMealSlot = i;
                    break;
                }
            }
            if (boneMealSlot == -1) return;
        }

        // Throttle: max 4 uses per second
        long now = System.currentTimeMillis();
        if (now - lastUseMs < 250) return;

        double range = rangeSetting.get();
        BlockPos center = client.player.getBlockPos();
        int ri = (int) Math.ceil(range);

        BlockPos target = null;
        double bestDistSq = range * range;

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Vec3d posVec = Vec3d.ofCenter(pos);
                    double distSq = posVec.squaredDistanceTo(client.player.getX(), client.player.getY(), client.player.getZ());
                    if (distSq > bestDistSq) continue;
                    if (!isValidTarget(client, pos)) continue;
                    bestDistSq = distSq;
                    target = pos;
                }
            }
        }

        if (target == null) return;

        // Switch to bonemeal if needed
        if (!holdingBoneMeal) {
            client.player.getInventory().setSelectedSlot(boneMealSlot);
        }

        // Use bonemeal on block
        Vec3d hitVec = Vec3d.ofCenter(target).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, target, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        client.player.swingHand(Hand.MAIN_HAND);

        lastUseMs = System.currentTimeMillis();
    }

    private boolean isValidTarget(MinecraftClient client, BlockPos pos) {
        var state = client.world.getBlockState(pos);
        Block block = state.getBlock();

        // Block must be bonemealable
        if (!(block instanceof Fertilizable fertilizable)) return false;
        if (!fertilizable.isFertilizable(client.world, pos, state)) return false;

        // Exclude grass (would just make tall grass, not useful)
        if (block instanceof GrassBlock) return false;

        // Category filters
        if (block instanceof SaplingBlock)   return saplingsSetting.get();
        if (block instanceof CropBlock)      return cropsSetting.get();
        if (block instanceof StemBlock)      return stemsSetting.get();
        if (block instanceof CocoaBlock)     return cocoaSetting.get();
        if (block instanceof SeaPickleBlock) return otherSetting.get(); // sea pickles in other

        return otherSetting.get();
    }
}
