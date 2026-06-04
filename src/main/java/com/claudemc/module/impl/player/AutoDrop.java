package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Automatically drops items matching configured categories from the hotbar/inventory.
 */
public class AutoDrop extends Module {

    public static AutoDrop INSTANCE;

    private int cooldown = 0;

    public AutoDrop() {
        super("AutoDrop", "Automatically drops certain item types", Category.PLAYER);
        addBool("Food",    false);
        addBool("Weapons", false);
        addBool("Armor",   false);
        addBool("Tools",   false);
        addBool("Junk",    false);
        INSTANCE = this;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (cooldown-- > 0) return;
        cooldown = 5;

        var inv    = client.player.getInventory();
        int syncId = client.player.playerScreenHandler.syncId;

        // Check all 36 inventory slots (0-8 hotbar, 9-35 main)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (shouldDrop(stack)) {
                int screenSlot = (i < 9) ? i + 36 : i;
                client.interactionManager.clickSlot(syncId, screenSlot, 1, SlotActionType.THROW, client.player);
                return;
            }
        }
    }

    private boolean shouldDrop(ItemStack stack) {
        if (bool("Food") && stack.get(DataComponentTypes.FOOD) != null)
            return true;

        if (bool("Weapons") && stack.get(DataComponentTypes.WEAPON) != null)
            return true;

        if (bool("Armor")) {
            var eq = stack.get(DataComponentTypes.EQUIPPABLE);
            if (eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR)
                return true;
        }

        if (bool("Tools") && stack.get(DataComponentTypes.TOOL) != null)
            return true;

        if (bool("Junk")) {
            var item = stack.getItem();
            if (item == Items.COBBLESTONE)  return true;
            if (item == Items.DIRT)         return true;
            if (item == Items.GRAVEL)       return true;
            if (item == Items.SAND)         return true;
            if (item == Items.NETHERRACK)   return true;
        }

        return false;
    }
}
