package com.claudemc.module.setting;

/**
 * An opaque free-text setting. Used as the fallback when a legacy string default cannot be
 * inferred as a boolean, number, or known option set. Not cycle-editable in the GUI.
 */
public class StringSetting extends Setting {

    private String value;

    public StringSetting(String name, String def) {
        super(name);
        this.value = def;
    }

    public String get()         { return value; }
    public void set(String v)   { this.value = v; }

    @Override public String asString()         { return value; }
    @Override public void fromString(String s) { this.value = s; }
    @Override public void onLeftClick()        { /* not editable */ }
    @Override public void onRightClick()       { /* not editable */ }
    @Override public boolean isEditable()      { return false; }
}
