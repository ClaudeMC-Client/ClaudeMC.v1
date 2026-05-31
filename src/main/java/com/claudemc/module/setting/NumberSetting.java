package com.claudemc.module.setting;

/**
 * A numeric setting bounded to [min, max]. Stores a double internally; when {@code integer}
 * is true it is rendered and stepped as a whole number. Left-click increments by one step,
 * right-click decrements, both clamped to the bounds.
 */
public class NumberSetting extends Setting {

    private double value;
    private final double min;
    private final double max;
    private final double step;
    private final boolean integer;

    public NumberSetting(String name, double def, double min, double max, double step, boolean integer) {
        super(name);
        this.min     = min;
        this.max     = max;
        this.step    = step <= 0 ? 1 : step;
        this.integer = integer;
        this.value   = clamp(def);
    }

    public double get()      { return value; }
    public int    getInt()   { return (int) Math.round(value); }
    public void   set(double v) { this.value = clamp(v); }

    @Override
    public String asString() {
        if (integer) return Integer.toString((int) Math.round(value));
        // Trim to at most 2 decimals without trailing zeros.
        String s = String.format(java.util.Locale.ROOT, "%.2f", value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    @Override
    public void fromString(String s) {
        try { this.value = clamp(Double.parseDouble(s.trim())); }
        catch (Exception ignored) { /* keep current value */ }
    }

    @Override public void onLeftClick()  { value = clamp(value + step); }
    @Override public void onRightClick() { value = clamp(value - step); }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }
}
