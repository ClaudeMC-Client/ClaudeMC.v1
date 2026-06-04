package com.claudemc.module.impl.world;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Excavator extends Module {

    public static Excavator INSTANCE;

    private BlockPos corner1 = null;
    private BlockPos corner2 = null;
    private boolean settingCorner = false; // true = setting corner1, false = setting corner2
    private boolean excavating = false;

    public Excavator() {
        super("Excavator", "Mines all blocks in a rectangular area", Category.WORLD);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (corner1 == null) {
            if (mc.player != null) {
                corner1 = mc.player.getBlockPos();
                mc.player.sendMessage(Text.literal("§aExcavator: Corner 1 set at " + corner1.toShortString()), false);
                mc.player.sendMessage(Text.literal("§eDisable and re-enable to set Corner 2"), false);
            }
            setEnabled(false);
        } else if (corner2 == null) {
            if (mc.player != null) {
                corner2 = mc.player.getBlockPos();
                mc.player.sendMessage(Text.literal("§aExcavator: Corner 2 set at " + corner2.toShortString()), false);
                mc.player.sendMessage(Text.literal("§aExcavating area..."), false);
                excavating = true;
            }
        }
    }

    @Override
    public void onDisable() {
        if (!excavating) {
            // Reset if manually disabled after corner1 set
        }
        excavating = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (corner1 == null || corner2 == null || !excavating) return;

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        boolean anyMined = false;
        for (int x = minX; x <= maxX && !anyMined; x++) {
            for (int y = minY; y <= maxY && !anyMined; y++) {
                for (int z = minZ; z <= maxZ && !anyMined; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = mc.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getHardness(mc.world, pos) < 0) continue;
                    mc.interactionManager.attackBlock(pos, Direction.UP);
                    anyMined = true;
                }
            }
        }

        if (!anyMined) {
            // Done excavating
            mc.player.sendMessage(Text.literal("§aExcavator: Done!"), false);
            corner1 = null;
            corner2 = null;
            excavating = false;
            setEnabled(false);
        }
    }
}
