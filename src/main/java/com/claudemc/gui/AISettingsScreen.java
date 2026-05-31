package com.claudemc.gui;

import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings screen for the AI integration.
 * Open via [AI] footer button in ClickGUI.
 *
 * Allows the user to:
 *  - Select the active provider (Anthropic / OpenAI / Gemini)
 *  - Enter/edit API keys for each provider
 *  - Optionally override the model name
 *  - Test the connection with a live ping
 */
public class AISettingsScreen extends Screen {

    private static final int C_BG     = 0xE5101018;
    private static final int C_HEADER = 0xFF18181F;
    private static final int C_ROW    = 0xFF111117;
    private static final int C_HOV    = 0xFF1C1C28;
    private static final int C_ACCENT = 0xFF44AAFF;
    private static final int C_TEXT   = 0xFFEEEEEE;
    private static final int C_SUB    = 0xFF9CA3AF;
    private static final int C_GREEN  = 0xFF4ADE80;
    private static final int C_WARN   = 0xFFFFAA44;
    private static final int C_RED    = 0xFFFF5555;
    private static final int C_INPUT  = 0xFF1E1E2A;

    // Which text field is active: 0=anthropicKey 1=openaiKey 2=geminiKey 3=model 4=maxTokens
    private int activeField = -1;

    private String testStatus = "";
    private int    testStatusColor = C_SUB;
    private final AtomicBoolean testing = new AtomicBoolean(false);

    // Editing buffers (synced to AIConfig on every keystroke)
    private final String[] labels    = {"Anthropic Key", "OpenAI Key", "Gemini Key", "Model (blank = default)", "Max Tokens"};
    private final String[] fieldKeys = {"anthropic",     "openai",     "gemini",     "model",                   "maxtokens"};

    private String getFieldValue(int i) {
        return switch (i) {
            case 0 -> AIConfig.INSTANCE.anthropicKey;
            case 1 -> AIConfig.INSTANCE.openaiKey;
            case 2 -> AIConfig.INSTANCE.geminiKey;
            case 3 -> AIConfig.INSTANCE.model;
            case 4 -> String.valueOf(AIConfig.INSTANCE.maxTokens);
            default -> "";
        };
    }

    private void setFieldValue(int i, String v) {
        switch (i) {
            case 0 -> AIConfig.INSTANCE.anthropicKey = v;
            case 1 -> AIConfig.INSTANCE.openaiKey    = v;
            case 2 -> AIConfig.INSTANCE.geminiKey    = v;
            case 3 -> AIConfig.INSTANCE.model        = v;
            case 4 -> { try { AIConfig.INSTANCE.maxTokens = Math.max(10, Integer.parseInt(v)); } catch (Exception ignored) {} }
        }
        AIConfig.save();
    }

    public AISettingsScreen() {
        super(Text.literal("ClaudeMC — AI Settings"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        ctx.fill(0, 0, width, 20, C_HEADER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§fAI Settings  §8|  §7Keys stored locally in §fconfig/claudemc/ai.json"),
            width / 2, 5, 0xFFFFFF);

        int y = 28;

        // Provider selector
        ctx.drawText(textRenderer, Text.literal("§7Provider:"), 8, y + 3, C_SUB, false);
        String[] providers = {"anthropic", "openai", "gemini"};
        String[] provLabels = {"Anthropic (Claude)", "OpenAI (GPT)", "Google Gemini"};
        int bx = 80;
        for (int i = 0; i < providers.length; i++) {
            boolean selected = providers[i].equals(AIConfig.INSTANCE.provider);
            boolean hover    = mx >= bx && mx < bx + 110 && my >= y && my < y + 16;
            int bg = selected ? C_ACCENT : (hover ? C_HOV : C_ROW);
            ctx.fill(bx, y, bx + 110, y + 16, bg);
            ctx.drawText(textRenderer,
                Text.literal((selected ? "§l" : "§7") + provLabels[i]),
                bx + 4, y + 4, C_TEXT, false);
            bx += 118;
        }
        y += 24;

        // Field inputs
        for (int i = 0; i < labels.length; i++) {
            ctx.drawText(textRenderer, Text.literal("§7" + labels[i] + ":"), 8, y + 4, C_SUB, false);
            boolean active = (activeField == i);
            ctx.fill(180, y, width - 8, y + 16, active ? 0xFF22224A : C_INPUT);
            ctx.fill(180, y, 181, y + 16, active ? C_ACCENT : C_SUB); // left indicator
            String val = getFieldValue(i);
            // Mask API keys
            String display = (i < 3 && val.length() > 8)
                ? val.substring(0, 4) + "···" + val.substring(val.length() - 4)
                : val;
            String cursor = active ? "§f|" : "";
            ctx.drawText(textRenderer, Text.literal("§f" + display + cursor), 184, y + 4, C_TEXT, false);
            y += 22;
        }

        // Current model label
        ctx.drawText(textRenderer,
            Text.literal("§8Active model: §7" + AIConfig.INSTANCE.resolvedModel()),
            8, y, C_SUB, false);
        y += 14;

        // Configured status
        boolean configured = AIConfig.INSTANCE.isConfigured();
        ctx.drawText(textRenderer,
            Text.literal(configured ? "§a✔ Key configured for " + AIConfig.INSTANCE.provider
                                    : "§c✘ No key set for active provider"),
            8, y, C_TEXT, false);
        y += 14;

        // Test button
        int testBtnX = 8, testBtnW = 110;
        boolean testHov = mx >= testBtnX && mx < testBtnX + testBtnW && my >= y && my < y + 16;
        ctx.fill(testBtnX, y, testBtnX + testBtnW, y + 16, testHov ? C_HOV : C_ROW);
        ctx.drawText(textRenderer,
            Text.literal(testing.get() ? "§7Testing…" : "§f[Test Connection]"),
            testBtnX + 4, y + 4, C_TEXT, false);

        if (!testStatus.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal(testStatus), testBtnX + testBtnW + 8, y + 4, testStatusColor, false);
        }

        y += 24;

        // Hint footer
        ctx.fill(0, height - 20, width, height, C_HEADER);
        ctx.drawText(textRenderer,
            Text.literal("§8Click a field to edit · Esc = save & close · Provider buttons switch which key is used"),
            8, height - 14, C_SUB, false);

        // Store test button Y for click detection
        this.testBtnY = y - 24;
    }

    private int testBtnY = 0;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = 28;

        // Provider selector
        String[] providers = {"anthropic", "openai", "gemini"};
        int bx = 80;
        for (String p : providers) {
            if (mx >= bx && mx < bx + 110 && my >= y && my < y + 16) {
                AIConfig.INSTANCE.provider = p;
                AIConfig.save();
                return true;
            }
            bx += 118;
        }
        y += 24;

        // Field inputs
        for (int i = 0; i < labels.length; i++) {
            if (mx >= 180 && mx < width - 8 && my >= y && my < y + 16) {
                activeField = (activeField == i) ? -1 : i;
                return true;
            }
            y += 22;
        }

        // Test button
        if (mx >= 8 && mx < 118 && my >= testBtnY && my < testBtnY + 16) {
            runTest();
            return true;
        }

        activeField = -1;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            AIConfig.save();
            if (client != null) client.setScreen(new ClickGui());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            activeField = (activeField + 1) % labels.length;
            return true;
        }
        if (activeField >= 0) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String cur = getFieldValue(activeField);
                if (!cur.isEmpty()) setFieldValue(activeField, cur.substring(0, cur.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                activeField = -1;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeField >= 0) {
            setFieldValue(activeField, getFieldValue(activeField) + chr);
            return true;
        }
        return false;
    }

    private void runTest() {
        if (!AIConfig.INSTANCE.isConfigured()) {
            testStatus = "§cNo key set";
            testStatusColor = C_RED;
            return;
        }
        testing.set(true);
        testStatus = "";

        AIClient.INSTANCE.ask("Reply with exactly the word: PONG",
            response -> {
                testing.set(false);
                testStatus = response.toLowerCase().contains("pong")
                    ? "§a✔ Connected! (" + AIConfig.INSTANCE.provider + ")"
                    : "§aConnected! Got: " + response.trim().substring(0, Math.min(30, response.length()));
                testStatusColor = C_GREEN;
            },
            err -> {
                testing.set(false);
                testStatus = "§c✘ " + err;
                testStatusColor = C_RED;
            }
        );
    }
}
