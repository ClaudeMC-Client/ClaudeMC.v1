package com.claudemc.module.setting;

import java.util.Arrays;
import java.util.List;

/** A multiple-choice setting that cycles through a fixed list of string options. */
public class ModeSetting extends Setting {

    private final List<String> options;
    private int index;

    public ModeSetting(String name, String def, String... opts) {
        super(name);
        this.options = Arrays.asList(opts);
        int i = options.indexOf(def);
        this.index = i >= 0 ? i : 0;
    }

    public String get() { return options.get(index); }

    public List<String> options() { return options; }

    @Override public String asString() { return options.get(index); }

    @Override
    public void fromString(String s) {
        int i = options.indexOf(s);
        if (i >= 0) index = i;
    }

    @Override public void onLeftClick()  { index = (index + 1) % options.size(); }
    @Override public void onRightClick() { index = (index - 1 + options.size()) % options.size(); }
}
