package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.lang.reflect.Field;

/**
 * Meteor Client-style Step — increases the player's max step height so they
 * can walk up full blocks without jumping.
 *
 * MC 1.20.x renamed the field from {@code stepHeight} to {@code maxUpStep}.
 * Both names are tried for broad compatibility.
 */
public class Step extends Module {

    private float savedStepHeight = 0.6f;
    private static final String[] FIELD_NAMES = {"maxUpStep", "stepHeight", "field_44825"};

    public Step() {
        super("Step", "Step up full blocks without jumping", Category.MOVEMENT);
        addNumber("Height", 1.0, 0.6, 3.0, 0.1, false);
    }

    @Override
    public void onEnable() {
        var c = MinecraftClient.getInstance();
        if (c.player == null) return;
        savedStepHeight = getStepHeight(c.player);
        setStepHeight(c.player, (float) Double.parseDouble(getSetting("Height")));
    }

    @Override
    public void onDisable() {
        var c = MinecraftClient.getInstance();
        if (c.player != null) setStepHeight(c.player, savedStepHeight);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        float h = (float) Double.parseDouble(getSetting("Height"));
        if (Math.abs(getStepHeight(client.player) - h) > 0.001f) setStepHeight(client.player, h);
    }

    private float getStepHeight(Entity entity) {
        for (String name : FIELD_NAMES) {
            try {
                Field f = findField(entity.getClass(), name);
                f.setAccessible(true);
                return f.getFloat(entity);
            } catch (Exception ignored) {}
        }
        return 0.6f;
    }

    private void setStepHeight(Entity entity, float value) {
        for (String name : FIELD_NAMES) {
            try {
                Field f = findField(entity.getClass(), name);
                f.setAccessible(true);
                f.setFloat(entity, value);
                return;
            } catch (Exception ignored) {}
        }
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
