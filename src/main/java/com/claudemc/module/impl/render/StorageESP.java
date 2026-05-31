package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class StorageESP extends BlockScanModule {

    public static StorageESP INSTANCE;

    public StorageESP() {
        super("StorageESP", "Shows container contents indicator through walls", Category.RENDER);
        addSetting("Radius", "32");
        addSetting("ShowFull",  "true");
        addSetting("ShowEmpty", "false");
        this.boxExpand = -0.05;   // slight inset so it nests inside the block
        INSTANCE = this;
    }

    @Override
    protected void scan(MinecraftClient client, List<Highlight> out) {
        int radius = radius(32);
        var pPos = client.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterateOutwards(pPos, radius, radius, radius)) {
            var be = client.world.getBlockEntity(pos);
            if (!isContainer(be)) continue;

            float fill = getFill(be);
            // Green = full, red = empty, yellow = partially full
            float r = fill < 0.1f ? 1f : (fill < 0.9f ? 1f : 0f);
            float g = fill < 0.1f ? 0f : (fill < 0.9f ? 0.8f : 1f);
            out.add(new Highlight(pos.getX(), pos.getY(), pos.getZ(), r, g, 0f));
        }
    }

    private boolean isContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity
            || be instanceof BarrelBlockEntity || be instanceof HopperBlockEntity
            || be instanceof FurnaceBlockEntity || be instanceof DispenserBlockEntity;
    }

    private float getFill(BlockEntity be) {
        if (be instanceof ChestBlockEntity c) {
            int used = 0;
            for (int i = 0; i < c.size(); i++) if (!c.getStack(i).isEmpty()) used++;
            return (float) used / c.size();
        }
        if (be instanceof ShulkerBoxBlockEntity s) {
            int used = 0;
            for (int i = 0; i < s.size(); i++) if (!s.getStack(i).isEmpty()) used++;
            return (float) used / s.size();
        }
        return 0.5f;
    }
}
