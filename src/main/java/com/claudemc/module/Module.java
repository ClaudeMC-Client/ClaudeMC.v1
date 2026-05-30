package com.claudemc.module;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    private final String   name;
    private final String   description;
    private final Category category;
    private boolean        enabled;

    // Simple key=value settings for ClickGUI rendering (name → value string)
    protected final List<String[]> settings = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this.name        = name;
        this.description = description;
        this.category    = category;
    }

    public final void toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else         onDisable();
    }

    public final void setEnabled(boolean v) {
        if (this.enabled != v) toggle();
    }

    protected void addSetting(String name, String defaultValue) {
        settings.add(new String[]{name, defaultValue});
    }

    public String getSetting(String name) {
        for (String[] s : settings) if (s[0].equals(name)) return s[1];
        return "";
    }

    public void setSetting(String name, String value) {
        for (String[] s : settings) if (s[0].equals(name)) { s[1] = value; return; }
    }

    public void onEnable()  {}
    public void onDisable() {}
    public abstract void onTick(MinecraftClient client);

    public String   getName()        { return name; }
    public String   getDescription() { return description; }
    public Category getCategory()    { return category; }
    public boolean  isEnabled()      { return enabled; }
    public List<String[]> getSettings() { return settings; }
}
