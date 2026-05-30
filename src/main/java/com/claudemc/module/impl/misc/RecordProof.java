package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Hides the Minecraft window from screen-capture tools on Windows.
 * Uses SetWindowDisplayAffinity(HWND, WDA_EXCLUDEFROMCAPTURE = 0x11).
 *
 * Supported: Discord screen share, OBS Window Capture, NVIDIA GameBar.
 * NOT supported: full-screen OBS game capture (captures GPU frame buffer).
 * Platform: Windows 10 v2004+ / Windows 11 only.
 *
 * Uses Java 21 Foreign Function & Memory API — no native library needed.
 */
public class RecordProof extends Module {

    private static final int WDA_NONE               = 0x00000000;
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private boolean platformSupported = false;
    private MethodHandle setWindowDisplayAffinity;

    public RecordProof() {
        super("RecordProof", "Hides window from Discord/OBS screen capture (Windows only)", Category.MISC);
        initNative();
    }

    private void initNative() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            ClaudeMCMod.LOGGER.info("[RecordProof] Not on Windows — module will have no effect.");
            return;
        }
        try {
            var arena   = Arena.ofAuto();
            var user32  = SymbolLookup.libraryLookup("user32.dll", arena);
            var sym     = user32.find("SetWindowDisplayAffinity").orElseThrow();
            setWindowDisplayAffinity = Linker.nativeLinker().downcallHandle(
                sym,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            platformSupported = true;
        } catch (Throwable t) {
            ClaudeMCMod.LOGGER.warn("[RecordProof] Could not load SetWindowDisplayAffinity: {}", t.getMessage());
        }
    }

    @Override
    public void onEnable() {
        setAffinity(WDA_EXCLUDEFROMCAPTURE);
        if (!platformSupported) {
            MinecraftClient.getInstance().player.sendMessage(
                net.minecraft.text.Text.literal("§c[RecordProof] Not supported on this platform."), false);
        }
    }

    @Override
    public void onDisable() {
        setAffinity(WDA_NONE);
    }

    private void setAffinity(int affinity) {
        if (!platformSupported || setWindowDisplayAffinity == null) return;
        try {
            long glfwWin = MinecraftClient.getInstance().getWindow().getHandle();
            long hwnd    = GLFWNativeWin32.glfwGetWin32Window(glfwWin);
            setWindowDisplayAffinity.invoke(MemorySegment.ofAddress(hwnd), affinity);
        } catch (Throwable t) {
            ClaudeMCMod.LOGGER.warn("[RecordProof] setAffinity failed: {}", t.getMessage());
        }
    }

    @Override public void onTick(MinecraftClient client) {}
}
