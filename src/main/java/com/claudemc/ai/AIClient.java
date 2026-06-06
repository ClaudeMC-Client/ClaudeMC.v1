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
     * @param onSuccess    Called with the response text
     * @param onFailure    Called with an error description
     */
    public void ask(String prompt, Consumer<String> onSuccess, Consumer<String> onFailure) {
        ask(null, prompt, onSuccess, onFailure);
    }

    /**
     * Sends {@code prompt} with an explicit system prompt override (thread-safe; does not
     * mutate AIConfig). Pass {@code null} to use the configured AIConfig system prompt.
     */
    public void ask(String systemPromptOverride, String prompt,
                    Consumer<String> onSuccess, Consumer<String> onFailure) {
        if (!AIConfig.INSTANCE.isConfigured()) {
            onFailure.accept("No API key configured — open [AI] in the ClickGUI.");
            return;
        }

        CompletableFuture.supplyAsync(() -> sendSync(systemPromptOverride, prompt))
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

    private String sendSync(String sysOverride, String prompt) {
        try {
            String sys = sysOverride != null ? sysOverride : AIConfig.INSTANCE.systemPrompt;
            return switch (AIConfig.INSTANCE.provider.toLowerCase()) {
                case "openai"  -> sendOpenAI(sys, prompt);
                case "gemini"  -> sendGemini(sys, prompt);
                default        -> sendAnthropic(sys, prompt);
            };
        } catch (Exception e) {
            ClaudeMCMod.LOGGER.warn("[AIClient] Request failed: {}", e.getMessage());
            return "\0ERR:" + e.getMessage();
        }
    }

    // ── Anthropic ────────────────────────────────────────────────────────

    private String sendAnthropic(String sys, String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", AIConfig.INSTANCE.resolvedModel());
        body.addProperty("max_tokens", AIConfig.INSTANCE.maxTokens);

        if (sys != null && !sys.isBlank()) body.addProperty("system", sys);

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
        if (resp.statusCode() != 200) return httpErr("Anthropic", resp);

        JsonObject json    = GSON.fromJson(resp.body(), JsonObject.class);
        JsonArray  content = json.has("content") ? json.getAsJsonArray("content") : null;
        if (content == null || content.isEmpty())
            return "\0ERR:Anthropic returned empty content";
        JsonObject first = content.get(0).getAsJsonObject();
        return first.has("text") ? first.get("text").getAsString()
                                 : "\0ERR:Anthropic missing text field";
    }

    // ── OpenAI ───────────────────────────────────────────────────────────

    private String sendOpenAI(String sys, String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", AIConfig.INSTANCE.resolvedModel());
        body.addProperty("max_tokens", AIConfig.INSTANCE.maxTokens);

        JsonArray messages = new JsonArray();

        if (sys != null && !sys.isBlank()) {
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
        if (resp.statusCode() != 200) return httpErr("OpenAI", resp);

        JsonObject json    = GSON.fromJson(resp.body(), JsonObject.class);
        JsonArray  choices = json.has("choices") ? json.getAsJsonArray("choices") : null;
        if (choices == null || choices.isEmpty())
            return "\0ERR:OpenAI returned empty choices";
        JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        return msg != null && msg.has("content") ? msg.get("content").getAsString()
                                                 : "\0ERR:OpenAI missing content field";
    }

    // ── Gemini ───────────────────────────────────────────────────────────

    private String sendGemini(String sys, String prompt) throws Exception {
        String modelId = AIConfig.INSTANCE.resolvedModel();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + modelId + ":generateContent?key=" + AIConfig.INSTANCE.geminiKey;

        // Prepend system prompt to user turn (Gemini free tier has no system role)
        String combined = (sys == null || sys.isBlank()) ? prompt : sys + "\n\n" + prompt;

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
        if (resp.statusCode() != 200) return httpErr("Gemini", resp);

        JsonObject json       = GSON.fromJson(resp.body(), JsonObject.class);
        JsonArray  candidates = json.has("candidates") ? json.getAsJsonArray("candidates") : null;
        if (candidates == null || candidates.isEmpty())
            return "\0ERR:Gemini returned empty candidates";
        JsonObject respContent = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (respContent == null) return "\0ERR:Gemini missing content";
        JsonArray respParts = respContent.has("parts") ? respContent.getAsJsonArray("parts") : null;
        if (respParts == null || respParts.isEmpty()) return "\0ERR:Gemini missing parts";
        JsonObject respPart = respParts.get(0).getAsJsonObject();
        return respPart.has("text") ? respPart.get("text").getAsString().trim()
                                    : "\0ERR:Gemini missing text";
    }

    /**
     * Builds an error string that includes the HTTP body so the actual reason is visible
     * (e.g. a 404 "model not found" or 400 "API key invalid"), rather than just the code.
     * The body is truncated to keep it readable in the chat bubble.
     */
    private static String httpErr(String provider, HttpResponse<String> resp) {
        String body = resp.body();
        if (body != null) {
            body = body.replaceAll("\\s+", " ").trim();
            if (body.length() > 300) body = body.substring(0, 300) + "…";
        }
        return "\0ERR:" + provider + " HTTP " + resp.statusCode()
             + (body == null || body.isEmpty() ? "" : " — " + body);
    }
}
