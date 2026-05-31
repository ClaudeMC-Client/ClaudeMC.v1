package com.claudemc.ai;

import com.claudemc.ClaudeMCMod;
import com.google.gson.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async HTTP client that sends prompts to Anthropic, OpenAI, or Gemini.
 *
 * All network calls run on a virtual thread and never block the game thread.
 * Results are delivered via callback lambdas.
 */
public final class AIClient {

    public static final AIClient INSTANCE = new AIClient();

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final Gson GSON = new Gson();

    private AIClient() {}

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Sends {@code prompt} asynchronously using the configured provider.
     * @param prompt       User message
     * @param onSuccess    Called on the game thread with the response text
     * @param onFailure    Called on the game thread with an error description
     */
    public void ask(String prompt, Consumer<String> onSuccess, Consumer<String> onFailure) {
        if (!AIConfig.INSTANCE.isConfigured()) {
            onFailure.accept("No API key configured — open [AI] in the ClickGUI.");
            return;
        }

        CompletableFuture.supplyAsync(() -> sendSync(prompt))
            .thenAccept(result -> {
                if (result.startsWith("\0ERR:")) onFailure.accept(result.substring(5));
                else                              onSuccess.accept(result);
            })
            .exceptionally(t -> {
                onFailure.accept(t.getMessage());
                return null;
            });
    }

    // ── Provider dispatch ────────────────────────────────────────────────

    private String sendSync(String prompt) {
        try {
            return switch (AIConfig.INSTANCE.provider.toLowerCase()) {
                case "openai"  -> sendOpenAI(prompt);
                case "gemini"  -> sendGemini(prompt);
                default        -> sendAnthropic(prompt);
            };
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIClient] Request failed: {}", e.getMessage());
            return "\0ERR:" + e.getMessage();
        }
    }

    // ── Anthropic ────────────────────────────────────────────────────────

    private String sendAnthropic(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", AIConfig.INSTANCE.resolvedModel());
        body.addProperty("max_tokens", AIConfig.INSTANCE.maxTokens);

        // System prompt
        String sys = AIConfig.INSTANCE.systemPrompt;
        if (!sys.isBlank()) body.addProperty("system", sys);

        // User message
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        messages.add(msg);
        body.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type",    "application/json")
            .header("x-api-key",       AIConfig.INSTANCE.anthropicKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return "\0ERR:Anthropic HTTP " + resp.statusCode();

        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        return json.getAsJsonArray("content").get(0).getAsJsonObject()
            .get("text").getAsString();
    }

    // ── OpenAI ───────────────────────────────────────────────────────────

    private String sendOpenAI(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", AIConfig.INSTANCE.resolvedModel());
        body.addProperty("max_tokens", AIConfig.INSTANCE.maxTokens);

        JsonArray messages = new JsonArray();

        String sys = AIConfig.INSTANCE.systemPrompt;
        if (!sys.isBlank()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", sys);
            messages.add(sysMsg);
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);
        body.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type",  "application/json")
            .header("Authorization", "Bearer " + AIConfig.INSTANCE.openaiKey)
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return "\0ERR:OpenAI HTTP " + resp.statusCode();

        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        return json.getAsJsonArray("choices").get(0).getAsJsonObject()
            .getAsJsonObject("message").get("content").getAsString();
    }

    // ── Gemini ───────────────────────────────────────────────────────────

    private String sendGemini(String prompt) throws Exception {
        String modelId = AIConfig.INSTANCE.resolvedModel();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + modelId + ":generateContent?key=" + AIConfig.INSTANCE.geminiKey;

        // Prepend system prompt to user turn (Gemini free tier has no system role)
        String sys = AIConfig.INSTANCE.systemPrompt;
        String combined = sys.isBlank() ? prompt : sys + "\n\n" + prompt;

        JsonObject part = new JsonObject();
        part.addProperty("text", combined);
        JsonArray parts = new JsonArray(); parts.add(part);

        JsonObject contentObj = new JsonObject();
        contentObj.addProperty("role", "user");
        contentObj.add("parts", parts);
        JsonArray contents = new JsonArray(); contents.add(contentObj);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", AIConfig.INSTANCE.maxTokens);

        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", genConfig);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return "\0ERR:Gemini HTTP " + resp.statusCode();

        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        return json.getAsJsonArray("candidates").get(0).getAsJsonObject()
            .getAsJsonObject("content")
            .getAsJsonArray("parts").get(0).getAsJsonObject()
            .get("text").getAsString().trim();
    }
}
