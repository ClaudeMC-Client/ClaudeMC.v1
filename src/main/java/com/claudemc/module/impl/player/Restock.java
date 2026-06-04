package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * When a chest is open, automatically moves items matching held-item types into the inventory.
 */
public class Restock extends Module {

    public static Restock INSTANCE;

    private int cooldown = 0;

    public Restock() {
        super("Restock", "Automatically restocks from open chests", Category.PLAYER);
        addBool("Food",    true);
        addBool("Weapons", false);
        addBool("Tools",   false);
        addBool("Blocks",  false);
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

        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler handler))
            return;

        int syncId    = handler.syncId;
        int chestSize = handler.getRows() * 9;

        for (int i = 0; i < chestSize; i++) {
            var slot  = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            if (shouldTake(stack)) {
                client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                cooldown = 5;
                return;
            }
        }
    }

    private boolean shouldTake(ItemStack stack) {
        if (bool("Food") && stack.get(net.minecraft.component.DataComponentTypes.FOOD) != null)
            return true;
        if (bool("Weapons") && stack.getItem() instanceof net.minecraft.item.SwordItem)
            return true;
        if (bool("Tools") && (stack.getItem() instanceof net.minecraft.item.PickaxeItem
                || stack.getItem() instanceof net.minecraft.item.AxeItem))
            return true;
        if (bool("Blocks") && stack.getItem() instanceof net.minecraft.item.BlockItem)
            return true;
        return false;
    }
}
