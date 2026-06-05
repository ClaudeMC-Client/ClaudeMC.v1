package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Kaboom extends Module {

    public static Kaboom INSTANCE;

    private int phase = 0; // 0=place TNT, 1=ignite, 2=done

    public Kaboom() {
        super("Kaboom", "Places and ignites TNT from hotbar", Category.WORLD);
        INSTANCE = this;
        addNumber("Count", 1, 1, 10, 1, true);
    }

    @Override
    public void onEnable() {
        phase = 0;
    }

    @Override
    public void onDisable() {
        phase = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int count = parseInt(getSetting("Count"), 1);

        if (phase == 0) {
            // Find TNT in hotbar
            int tntSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == Items.TNT) {
                    tntSlot = i;
                    break;
                }
            }
            if (tntSlot < 0) {
                mc.player.sendMessage(Text.literal("§cKaboom: No TNT in hotbar!"), false);
                setEnabled(false);
                return;
            }
            mc.player.getInventory().setSelectedSlot(tntSlot);

            BlockPos playerPos = mc.player.getBlockPos();
            // Place TNT on the block below player
            BlockPos below = playerPos.down();
            for (int i = 0; i < count; i++) {
                BlockPos target = playerPos.add(i, 0, 0);
                // Find a surface to place on
                BlockPos surface = target.down();
                if (!mc.world.getBlockState(surface).isAir() && mc.world.getBlockState(target).isAir()) {
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(surface), Direction.UP, surface, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                }
            }
            phase = 1;

        } else if (phase == 1) {
            // Find flint and steel in hotbar
            int flintSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && (stack.getItem() == Items.FLINT_AND_STEEL
                        || stack.getItem() == Items.FIRE_CHARGE)) {
                    flintSlot = i;
                    break;
                }
            }

            if (flintSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(flintSlot);
                // Ignite TNT blocks near player
                BlockPos playerPos = mc.player.getBlockPos();
                for (int i = 0; i < count; i++) {
                    BlockPos tntPos = playerPos.add(i, 0, 0);
                    if (mc.world.getBlockState(tntPos).getBlock() == Blocks.TNT) {
                        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(tntPos), Direction.UP, tntPos, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    }
                }
            } else {
                // No lighter — use game command to ignite (send /setblock)
                mc.player.sendMessage(Text.literal("§eKaboom: No flint & steel, TNT placed but not lit."), false);
            }

            phase = 2;
            setEnabled(false);
        }
    }

    private int parseInt(String s, int d) {
        try { return (int) Double.parseDouble(s); } catch (Exception e) { return d; }
    }
}
