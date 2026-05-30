package com.claudemc.module;

import net.minecraft.client.MinecraftClient;

public abstract class Module {

    private final String name;
    private final String description;
    private final String category;
    private boolean enabled;

    public Module(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public final void toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public final void setEnabled(boolean value) {
        if (this.enabled != value) toggle();
    }

    public void onEnable() {}
    public void onDisable() {}

    /** Called every client tick while this module is enabled. */
    public abstract void onTick(MinecraftClient client);

    public String getName()        { return name; }
    public String getDescription() { return description; }
    public String getCategory()    { return category; }
    public boolean isEnabled()     { return enabled; }
}
