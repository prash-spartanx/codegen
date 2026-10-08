package com.prashant.codegen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.codegen.schema.ProjectSpecSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient implements LlmClient{

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private record ParsedResult(String text, String finishReason) {}
    @Override
    public String getProviderName() {
        return "gemini";
    }

    /**
     * Generates Python method code using Gemini with retry logic, rate limit handling,
     * zero-thinking budget config, safe JSON parsing, and method body completeness validation.
     */
    @Override
    public String generateCode(String modelName, String prompt) throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        // Fix 1: Explicitly set thinkingBudget to 0 and maxOutputTokens to 800
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", 800,
                        "temperature", 0.2,
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );

        String jsonBody = mapper.writeValueAsString(requestBody);
        int maxAttempts = 4;

        // Fix 2: Retry & exponential backoff for HTTP 503 and 429
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                // Fix 3: Safe parsing of candidates and finishReason check
                ParsedResult parsed = parseCandidate(response.body());
                String code = parsed.text();

                // Fix 4: Validate code completeness before accepting result
                if (!looksLikeCompleteMethodBody(code)) {
                    throw new IOException("Generated method body failed syntax/completeness validation:\n" + code);
                }

                return code;
            }

            if (status == 503 || status == 429) {
                long delayMs = extractRetryDelayMs(response.body(), attempt);
                System.err.println("WARN: HTTP " + status + " on attempt " + attempt + ", retrying in " + delayMs + "ms");
                Thread.sleep(delayMs);
                continue;
            }

            throw new IOException("Gemini API error " + status + ": " + response.body());
        }

        throw new IOException("Exceeded max retry attempts (" + maxAttempts + ") for Gemini call");
    }

    /**
     * Extracts retry delay from Gemini error payload (/error/details) with fallback to exponential backoff.
     */
    private long extractRetryDelayMs(String body, int attempt) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode retryInfo = root.at("/error/details");
            if (retryInfo.isArray()) {
                for (JsonNode detail : retryInfo) {
                    String delayStr = detail.path("retryDelay").asText("");
                    if (delayStr.endsWith("s")) {
                        String s = delayStr.replace("s", "");
                        return (long) (Double.parseDouble(s) * 1000) + 500; // Small buffer included
                    }
                }
            }
        } catch (Exception e) {
            // Fallback if error parsing fails
        }
        return (long) Math.pow(2, attempt) * 1000; // Exponential fallback
    }

    /**
     * Safely validates and parses candidate output from Gemini API responses.
     */
    private ParsedResult parseCandidate(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IOException("No candidates in response: " + body);
        }

        JsonNode candidate = candidates.get(0);
        String finishReason = candidate.path("finishReason").asText("UNKNOWN");

        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IOException("Empty content/parts (finishReason=" + finishReason + "): " + body);
        }

        String text = parts.get(0).path("text").asText(null);
        if (text == null || text.isBlank()) {
            throw new IOException("Blank candidate text (finishReason=" + finishReason + ")");
        }

        if (!"STOP".equals(finishReason)) {
            throw new IOException("Incomplete generation, finishReason=" + finishReason);
        }

        return new ParsedResult(text, finishReason);
    }

    /**
     * Sanity check to verify whether generated code is complete and balanced.
     */
    private boolean looksLikeCompleteMethodBody(String body) {
        if (body == null || body.isBlank()) return false;

        long openParens = body.chars().filter(c -> c == '(').count();
        long closeParens = body.chars().filter(c -> c == ')').count();
        if (openParens != closeParens) return false;

        String trimmed = body.stripTrailing();
        if (trimmed.endsWith("=") || trimmed.endsWith(",") || trimmed.endsWith("(")) return false;

        return true;
    }

    /**
     * Generates the structured project specification JSON.
     */
    @Override
    public String generateProjectSpec(String modelName, String description)
            throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        String systemInstructionText = """
                You are an expert software architect.

                Convert the user's application description into
                a ProjectSpec for our FastAPI code generator.

                Follow the provided response schema exactly.

                Make architectural decisions based on the user's
                requirements. Do not invent unnecessary features.
                """;

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstructionText))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", description)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", ProjectSpecSchema.getSchema(),
                        "temperature", 0.2
                )
        );

        String jsonBody = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }

        ParsedResult parsed = parseCandidate(response.body());
        return parsed.text();
    }
}