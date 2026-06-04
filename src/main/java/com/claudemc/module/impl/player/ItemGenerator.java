package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Generates items via the creative inventory (creative mode only).
 * Picks a configured item and fills empty hotbar slots with it.
 */
public class ItemGenerator extends Module {

    public static ItemGenerator INSTANCE;

    private int cooldown = 0;

    public ItemGenerator() {
        super("ItemGenerator", "Generates items via creative inventory (creative only)", Category.EXPLOIT);
        addSetting("Item", "diamond");
        addNumber("Slot", 0, 0, 8, 1, true);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!client.player.getAbilities().creativeMode) return;
        if (cooldown-- > 0) return;
        cooldown = 10;

        int targetSlot = parseInt(getSetting("Slot"), 0);
        var inv = client.player.getInventory();
        ItemStack current = inv.getStack(targetSlot);

        if (!current.isEmpty()) return;  // slot already filled

        var item = net.minecraft.registry.Registries.ITEM.get(
            net.minecraft.util.Identifier.tryParse(getSetting("Item")));
        if (item == null || item == Items.AIR) return;

        // In creative mode, clickSlot with CLONE duplicates the item into the cursor
        // then places it into the target slot
        int screenSlot = targetSlot + 36;
        client.interactionManager.clickCreativeStack(new ItemStack(item, 64), screenSlot);
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
