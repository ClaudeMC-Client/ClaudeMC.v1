package com.claudemc.module.setting;

/**
 * A typed, GUI-editable module setting.
 *
 * Values are still exposed as strings via {@link #asString()} / {@link #fromString(String)}
 * so existing modules can keep reading them with {@code Module.getSetting(name)} and parsing
 * as before, while the ClickGUI and persistence layer operate on the typed object.
 */
public abstract class Setting {

    protected final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    /** Current value rendered as a string (used for display and persistence). */
    public abstract String asString();

    /** Restore the value from its string form (persistence / API). Invalid input is ignored. */
    public abstract void fromString(String s);

    /** GUI: left-click action — toggle / increment / next option. */
    public abstract void onLeftClick();

    /** GUI: right-click action — toggle / decrement / previous option. */
    public abstract void onRightClick();

    /** Whether this setting can be cycled in the GUI (false for opaque string settings). */
    public boolean isEditable() { return true; }
}
