package com.claudemc.module.setting;

/** A boolean on/off setting. Either click toggles it. */
public class BoolSetting extends Setting {

    private boolean value;

    public BoolSetting(String name, boolean def) {
        super(name);
        this.value = def;
    }

    public boolean get()        { return value; }
    public void set(boolean v)  { this.value = v; }

    @Override public String asString()            { return Boolean.toString(value); }
    @Override public void fromString(String s)    { this.value = Boolean.parseBoolean(s); }
    @Override public void onLeftClick()           { value = !value; }
    @Override public void onRightClick()          { value = !value; }
}
