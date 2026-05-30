package com.claudemc.module;

public enum Category {
    COMBAT   ("Combat",   0xFF_FF4444),
    MOVEMENT ("Movement", 0xFF_44AAFF),
    PLAYER   ("Player",   0xFF_FFAA44),
    RENDER   ("Render",   0xFF_44FF88),
    WORLD    ("World",    0xFF_FF44FF),
    MISC     ("Misc",     0xFF_AAAAAA);

    public final String displayName;
    public final int    color;

    Category(String displayName, int color) {
        this.displayName = displayName;
        this.color       = color;
    }
}
