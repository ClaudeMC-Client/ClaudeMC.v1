package com.claudemc.module.setting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for the typed setting system (no Minecraft runtime). */
class SettingTest {

    @Test
    void numberSettingClampsToBounds() {
        NumberSetting n = new NumberSetting("Radius", 32, 0, 64, 1, true);
        assertEquals("32", n.asString());

        // Cannot exceed max.
        for (int i = 0; i < 100; i++) n.onLeftClick();
        assertEquals("64", n.asString());

        // Cannot go below min.
        for (int i = 0; i < 200; i++) n.onRightClick();
        assertEquals("0", n.asString());
    }

    @Test
    void numberSettingFormatsDecimalsWithoutTrailingZeros() {
        NumberSetting d = new NumberSetting("Speed", 0.2, 0, 10, 0.05, false);
        assertEquals("0.2", d.asString());
        d.onLeftClick();                 // 0.2 -> 0.25
        assertEquals("0.25", d.asString());
    }

    @Test
    void numberSettingIgnoresInvalidFromString() {
        NumberSetting n = new NumberSetting("R", 10, 0, 100, 1, true);
        n.fromString("not-a-number");
        assertEquals("10", n.asString());
        n.fromString("42");
        assertEquals("42", n.asString());
    }

    @Test
    void modeSettingCyclesAndWraps() {
        ModeSetting m = new ModeSetting("Filter", "Players", "Players", "Hostile", "All");
        assertEquals("Players", m.get());
        m.onLeftClick();
        assertEquals("Hostile", m.get());
        m.onLeftClick();
        assertEquals("All", m.get());
        m.onLeftClick();                 // wrap back to first
        assertEquals("Players", m.get());
        m.onRightClick();                // wrap to last
        assertEquals("All", m.get());
    }

    @Test
    void modeSettingDefaultsToFirstWhenDefaultUnknown() {
        ModeSetting m = new ModeSetting("X", "missing", "a", "b");
        assertEquals("a", m.get());
    }

    @Test
    void boolSettingToggles() {
        BoolSetting b = new BoolSetting("On", false);
        assertFalse(b.get());
        b.onLeftClick();
        assertTrue(b.get());
        b.onRightClick();
        assertFalse(b.get());
        b.fromString("true");
        assertTrue(b.get());
    }

    @Test
    void stringSettingIsEditableInGui() {
        StringSetting s = new StringSetting("Cmd", "/say hi");
        assertTrue(s.isEditable());     // editable via ClickGui inline text editor
        s.onLeftClick();                // no-op at setting level; ClickGui handles it
        assertEquals("/say hi", s.asString());
        s.set("hello");
        assertEquals("hello", s.asString());
    }
}
