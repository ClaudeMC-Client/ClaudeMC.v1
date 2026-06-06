package com.claudemc.module.impl.misc;

import com.claudemc.ClaudeMCMod;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Hides the Minecraft window from screen-capture tools on Windows.
 * Uses SetWindowDisplayAffinity(HWND, WDA_EXCLUDEFROMCAPTURE = 0x11) via PowerShell.
 *
 * Works with: Discord "Window" capture, OBS "Window Capture".
 * Does NOT work with: Discord/OBS "Screen" (desktop) capture — that bypasses affinity.
 * Platform: Windows 10 v2004 (20H1) / Windows 11 only.
 */
public class RecordProof extends Module {

    private boolean active = false;

    public RecordProof() {
        super("RecordProof", "Hides window from Discord/OBS screen capture (Windows only)", Category.UTILITY);
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
            var p = MinecraftClient.getInstance().player;
            if (p != null) p.sendMessage(
                Text.literal("§a[RecordProof] §7Window hidden. §8Use Discord 'Window' capture, not 'Screen' capture."), false);
            ClaudeMCMod.LOGGER.info("[RecordProof] Window hidden from capture.");
        } else {
            var p = MinecraftClient.getInstance().player;
            if (p != null) p.sendMessage(
                Text.literal("§c[RecordProof] Failed — Windows 10 v2004 (20H1) or newer required."), false);
            setEnabled(false);
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
            long hwnd = getHwnd();
            if (hwnd == 0) return false;
            // Guard Add-Type with a type-exists check so toggling the module multiple times
            // in the same session doesn't fail because ClaudeMC.WinAPI is already compiled.
            String ps = String.format(
                "if (-not ([System.Management.Automation.PSTypeName]'ClaudeMC.WinAPI').Type)" +
                "  { Add-Type -MemberDefinition '%s' -Name WinAPI -Namespace ClaudeMC };" +
                "[ClaudeMC.WinAPI]::SetWindowDisplayAffinity([IntPtr]%d, %d)",
                "[DllImport(\"user32.dll\")] public static extern bool SetWindowDisplayAffinity(IntPtr hwnd, uint affinity);",
                hwnd, affinity);
            int exit = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                .redirectErrorStream(true)
                .start()
                .waitFor();
            return exit == 0;
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
