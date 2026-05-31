package com.claudemc.module;

import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.ModeSetting;
import com.claudemc.module.setting.NumberSetting;
import com.claudemc.module.setting.Setting;
import com.claudemc.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    private final String   name;
    private final String   description;
    private final Category category;
    private boolean        enabled;

    // Typed, GUI-editable settings. Values remain readable as strings via getSetting().
    protected final List<Setting> settings = new ArrayList<>();

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

    // ── Setting registration ────────────────────────────────────────────────

    /**
     * Legacy registration. Infers a typed setting from the default value so existing
     * modules become GUI-editable without code changes:
     *   "true"/"false" → BoolSetting · integer → NumberSetting(int) · decimal → NumberSetting
     *   anything else  → opaque StringSetting (displayed but not cycle-editable).
     */
    protected Setting addSetting(String name, String def) {
        Setting s = infer(name, def);
        settings.add(s);
        return s;
    }

    protected BoolSetting addBool(String name, boolean def) {
        BoolSetting s = new BoolSetting(name, def);
        settings.add(s);
        return s;
    }

    protected NumberSetting addNumber(String name, double def, double min, double max,
                                      double step, boolean integer) {
        NumberSetting s = new NumberSetting(name, def, min, max, step, integer);
        settings.add(s);
        return s;
    }

    protected ModeSetting addMode(String name, String def, String... options) {
        ModeSetting s = new ModeSetting(name, def, options);
        settings.add(s);
        return s;
    }

    private static Setting infer(String name, String def) {
        if (def.equalsIgnoreCase("true") || def.equalsIgnoreCase("false")) {
            return new BoolSetting(name, Boolean.parseBoolean(def));
        }
        try {
            int i = Integer.parseInt(def.trim());
            int max = Math.max(1024, Math.abs(i) * 4);
            return new NumberSetting(name, i, 0, max, 1, true);
        } catch (NumberFormatException ignored) { /* not an int */ }
        try {
            double d = Double.parseDouble(def.trim());
            double max = Math.max(100.0, Math.abs(d) * 4);
            return new NumberSetting(name, d, 0, max, 0.05, false);
        } catch (NumberFormatException ignored) { /* not a decimal */ }
        return new StringSetting(name, def);
    }

    // ── Setting access (string-based, backward compatible) ───────────────────

    public String getSetting(String name) {
        for (Setting s : settings) if (s.getName().equals(name)) return s.asString();
        return "";
    }

    public void setSetting(String name, String value) {
        for (Setting s : settings) if (s.getName().equals(name)) { s.fromString(value); return; }
    }

    public Setting getSettingObj(String name) {
        for (Setting s : settings) if (s.getName().equals(name)) return s;
        return null;
    }

    public void onEnable()  {}
    public void onDisable() {}
    public abstract void onTick(MinecraftClient client);

    public String   getName()        { return name; }
    public String   getDescription() { return description; }
    public Category getCategory()    { return category; }
    public boolean  isEnabled()      { return enabled; }
    public List<Setting> getSettings() { return settings; }
}
