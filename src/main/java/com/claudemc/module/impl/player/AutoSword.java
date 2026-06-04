package com.claudemc.module.impl.player;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Automatically switches to a sword when targeting a hostile/passive mob or player.
 */
public class AutoSword extends Module {

    public static AutoSword INSTANCE;

    private int prevSlot = -1;

    public AutoSword() {
        super("AutoSword", "Switches to sword when attacking entities", Category.PLAYER);
        addBool("Animals",  false);
        addBool("Players",  true);
        addBool("SwitchBack", true);
        INSTANCE = this;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(getSetting(name));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        HitResult hit = client.crosshairTarget;
        boolean targetingEntity = hit != null && hit.getType() == HitResult.Type.ENTITY;

        if (targetingEntity) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            boolean relevant = (entity instanceof MobEntity)
                || (bool("Animals") && entity instanceof AnimalEntity)
                || (bool("Players") && entity instanceof PlayerEntity);

            if (relevant) {
                int swordSlot = findSwordSlot(client);
                if (swordSlot != -1 && swordSlot != client.player.getInventory().getSelectedSlot()) {
                    if (prevSlot == -1) prevSlot = client.player.getInventory().getSelectedSlot();
                    client.player.getInventory().setSelectedSlot(swordSlot);
                }
                return;
            }
        }

        // Not targeting a relevant entity — switch back
        if (prevSlot != -1 && bool("SwitchBack")) {
            client.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private int findSwordSlot(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isIn(ItemTags.SWORDS)) return i;
        }
        return -1;
    }
}
