package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Hides the Minecraft window from screen-capture tools on Windows.
 * Uses SetWindowDisplayAffinity(HWND, WDA_EXCLUDEFROMCAPTURE = 0x11) via JNA/shell.
 * Supported: Discord screen share, OBS Window Capture.
 * Platform: Windows 10 v2004+ / Windows 11 only.
 */
public class RecordProof extends Module {

    private boolean active = false;

    public RecordProof() {
        super("RecordProof", "Hides window from Discord/OBS screen capture (Windows only)", Category.CHAT);
    }

    @Override
    public void onEnable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!windows) {
            var p = MinecraftClient.getInstance().player;
            if (p != null) p.sendMessage(
                Text.literal("§c[RecordProof] Windows 10 v2004+ only."), false);
            setEnabled(false);
            return;
        }
        if (setAffinity(0x00000011)) {
            active = true;
            ClaudeMCMod.LOGGER.info("[RecordProof] Window hidden from capture.");
        }
    }

    @Override
    public void onDisable() {
        if (active) {
            setAffinity(0x00000000);
            active = false;
        }
    }

    @Override public void onTick(MinecraftClient client) {}

    private boolean setAffinity(int affinity) {
        try {
            // Use PowerShell + the Add-Type C# bridge to call SetWindowDisplayAffinity.
            // Avoids the need for the FFM API at compile time.
            long hwnd = getHwnd();
            if (hwnd == 0) return false;
            String cs = String.format(
                "[DllImport(\"user32.dll\")] public static extern bool SetWindowDisplayAffinity(IntPtr hwnd, uint affinity);",
                new Object[0]);
            String ps = String.format(
                "Add-Type -MemberDefinition '%s' -Name WinAPI -Namespace ClaudeMC;" +
                "[ClaudeMC.WinAPI]::SetWindowDisplayAffinity([IntPtr]%d, %d)",
                cs, hwnd, affinity);
            new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                .redirectErrorStream(true)
                .start()
                .waitFor();
            return true;
        } catch (Throwable t) {
            ClaudeMCMod.LOGGER.warn("[RecordProof] setAffinity failed: {}", t.getMessage());
            return false;
        }
    }

    private long getHwnd() {
        try {
            // GLFWNativeWin32 is only present on Windows natives — guard with reflection
            Class<?> glfwWin32 = Class.forName("org.lwjgl.glfw.GLFWNativeWin32");
            long glfwHandle = MinecraftClient.getInstance().getWindow().getHandle();
            return (long) glfwWin32.getMethod("glfwGetWin32Window", long.class).invoke(null, glfwHandle);
        } catch (Throwable t) {
            return 0;
        }
    }
}
