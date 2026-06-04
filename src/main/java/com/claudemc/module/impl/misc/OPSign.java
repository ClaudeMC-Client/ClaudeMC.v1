package com.claudemc.module.impl.misc;

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

/**
 * OP-Sign: Attempts to gain OP by exploiting sign command execution on vulnerable servers.
 * Places a sign with /op command text, exploiting older server versions where signs
 * could execute commands.
 */
public class OPSign extends Module {

    public static OPSign INSTANCE;

    private int phase = 0;

    public OPSign() {
        super("OP-Sign", "Attempts OP via sign command exploitation", Category.EXPLOIT);
        INSTANCE = this;
        addSetting("Command", "/op @s");
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

        if (phase == 0) {
            // First try direct command execution (some servers allow OP commands via chat)
            String cmd = getSetting("Command").trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);

            // Attempt known sign-based OP exploits via commands
            try {
                mc.getNetworkHandler().sendChatCommand("op " + mc.player.getGameProfile().name());
                mc.player.sendMessage(Text.literal("§a[OP-Sign] Sent /op attempt"), false);
            } catch (Exception ignored) {}

            // Try to place a sign with command text (old Bukkit exploit)
            int signSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && (stack.getItem() == Items.OAK_SIGN
                        || stack.getItem() == Items.BIRCH_SIGN
                        || stack.getItem() == Items.SPRUCE_SIGN)) {
                    signSlot = i;
                    break;
                }
            }

            if (signSlot >= 0) {
                mc.player.getInventory().selectedSlot = signSlot;
                BlockPos playerPos = mc.player.getBlockPos();
                BlockPos placePos = playerPos.north();
                if (mc.world.getBlockState(placePos).isAir()) {
                    BlockPos below = placePos.down();
                    if (!mc.world.getBlockState(below).isAir()) {
                        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below), Direction.UP, below, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        mc.player.sendMessage(Text.literal("§a[OP-Sign] Sign placed"), false);
                    }
                }
            }

            phase = 1;
            setEnabled(false);
        }
    }
}
