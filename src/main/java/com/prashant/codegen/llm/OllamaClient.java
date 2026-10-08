package com.prashant.codegen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.codegen.generator.ServiceLogicGenerator;
import com.prashant.codegen.llm.LlmClient;
import com.prashant.codegen.llm.LlmOutputCleaner;
import com.prashant.codegen.schema.ProjectSpecSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaClient implements LlmClient {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private static final int CONTEXT_SIZE = 14_000;
    private static final int METHOD_MAX_OUTPUT_TOKENS = 2_500;

    @Override
    public String getProviderName() {
        return "ollama";
    }

    /**
     * Generates a Python method body using a locally running Ollama model.
     *
     * The model is requested to return:
     *
     * {
     *   "methodBody": "..."
     * }
     *
     * The JSON wrapper allows the Java client to safely extract the
     * generated method body without depending on markdown/code fences.
     */
    @Override
    public String generateCode(
            String modelName,
            String prompt)
            throws IOException, InterruptedException {

        String url = baseUrl + "/api/generate";

        String structuredInstruction = prompt + """

                IMPORTANT OUTPUT FORMAT:
                Return a JSON object with exactly one field named "methodBody".

                Example:
                {
                  "methodBody": "        project = Project(...)\\n        return project"
                }

                Do not return markdown.
                Do not return code fences.
                Do not add any fields.
                """;

        Map<String, Object> options = Map.of(
                "temperature", 0.2,
                "num_ctx", CONTEXT_SIZE,
                "num_predict", METHOD_MAX_OUTPUT_TOKENS
        );

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "prompt", structuredInstruction,
                "stream", false,
                "format", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "methodBody", Map.of(
                                        "type", "string"
                                )
                        ),
                        "required", List.of("methodBody")
                ),
                "options", options
        );

        String jsonBody = mapper.writeValueAsString(requestBody);

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );
//            // print the response body for debugging
//            System.out.println("Ollama response body: " + response.body());
            int status = response.statusCode();

            if (status == 200) {

                String methodBody =
                        parseMethodBody(response.body());
//                // print the extracted method body for debugging
//                System.out.println("Extracted method body: " + methodBody);

                if (!looksLikeCompleteMethodBody(methodBody)) {
                    throw new IOException(
                            "Generated method body failed syntax/completeness validation:\n"
                                    + methodBody
                    );
                }

                return methodBody;
            }

            /*
             * Ollama is local, so HTTP 429/503 generally indicates that
             * the local server is temporarily unavailable or overloaded.
             */
            if (status == 429 || status == 503) {

                long delayMs =
                        (long) Math.pow(2, attempt) * 1000;

                System.err.println(
                        "WARN: Ollama HTTP "
                                + status
                                + " on attempt "
                                + attempt
                                + ", retrying in "
                                + delayMs
                                + "ms"
                );

                Thread.sleep(delayMs);
                continue;
            }

            throw new IOException(
                    "Ollama API error "
                            + status
                            + ": "
                            + response.body()
            );
        }

        throw new IOException(
                "Exceeded max retry attempts ("
                        + maxAttempts
                        + ") for Ollama call"
        );
    }

    /**
     * Extracts the generated method body from Ollama's response.
     */
    private String parseMethodBody(String responseBody)
            throws IOException {

        JsonNode root =
                mapper.readTree(responseBody);

        JsonNode responseNode =
                root.path("response");

        if (responseNode.isMissingNode()
                || responseNode.isNull()) {

            throw new IOException(
                    "Ollama response does not contain a response field: "
                            + responseBody
            );
        }

        String generatedJson =
                responseNode.asText();

        if (generatedJson == null
                || generatedJson.isBlank()) {

            throw new IOException(
                    "Ollama returned an empty response"
            );
        }

        JsonNode structuredResult =
                mapper.readTree(generatedJson);

        JsonNode methodBodyNode =
                structuredResult.path("methodBody");

        if (methodBodyNode.isMissingNode()
                || methodBodyNode.isNull()) {

            throw new IOException(
                    "Ollama structured response does not contain "
                            + "'methodBody': "
                            + generatedJson
            );
        }

        String methodBody =
                methodBodyNode.asText();

        if (methodBody == null
                || methodBody.isBlank()) {

            throw new IOException(
                    "Ollama returned an empty methodBody"
            );
        }

        return methodBody;
    }

    /**
     * Basic sanity validation.
     *
     * Full Python formatting/indentation is handled by
     * LlmOutputCleaner and Black.
     */
    private boolean looksLikeCompleteMethodBody(
            String body) {

        if (body == null || body.isBlank()) {
            return false;
        }

        long openParens =
                body.chars()
                        .filter(c -> c == '(')
                        .count();

        long closeParens =
                body.chars()
                        .filter(c -> c == ')')
                        .count();

        if (openParens != closeParens) {
            return false;
        }

        long openBrackets =
                body.chars()
                        .filter(c -> c == '[')
                        .count();

        long closeBrackets =
                body.chars()
                        .filter(c -> c == ']')
                        .count();

        if (openBrackets != closeBrackets) {
            return false;
        }

        long openBraces =
                body.chars()
                        .filter(c -> c == '{')
                        .count();

        long closeBraces =
                body.chars()
                        .filter(c -> c == '}')
                        .count();

        if (openBraces != closeBraces) {
            return false;
        }

        String trimmed =
                body.stripTrailing();

        if (trimmed.endsWith("=")
                || trimmed.endsWith(",")
                || trimmed.endsWith("(")
                || trimmed.endsWith("[")
                || trimmed.endsWith("{")) {

            return false;
        }

        return true;
    }

    /**
     * Generates the structured ProjectSpec JSON using Ollama.
     *
     * Ollama receives the ProjectSpec schema as a JSON-schema format
     * definition and is instructed to return JSON matching that schema.
     */
    @Override
    public String generateProjectSpec(
            String modelName,
            String description)
            throws IOException, InterruptedException {

        String url = baseUrl + "/api/generate";

        String systemInstruction = """
                You are an expert software architect.

                Convert the user's application description into
                a ProjectSpec for our FastAPI code generator.

                Follow the provided JSON schema exactly.

                Make architectural decisions based on the user's
                requirements. Do not invent unnecessary features.
                """;

        String prompt = systemInstruction
                + "\n\nProjectSpec JSON schema:\n"
                + mapper.writeValueAsString(
                ProjectSpecSchema.getSchema()
        )
                + "\n\nUser application description:\n"
                + description;

        Map<String, Object> options = Map.of(
                "temperature", 0.2,
                "num_ctx", CONTEXT_SIZE,
                "num_predict", 8_000
        );

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "prompt", prompt,
                "stream", false,
                "format", ProjectSpecSchema.getSchema(),
                "options", options
        );

        String jsonBody =
                mapper.writeValueAsString(requestBody);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(10))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(jsonBody)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {

            throw new IOException(
                    "Ollama API error: "
                            + response.statusCode()
                            + " - "
                            + response.body()
            );
        }

        JsonNode root =
                mapper.readTree(response.body());

        JsonNode responseNode =
                root.path("response");

        if (responseNode.isMissingNode()
                || responseNode.isNull()
                || responseNode.asText().isBlank()) {

            throw new IOException(
                    "Ollama returned empty ProjectSpec response: "
                            + response.body()
            );
        }

        return responseNode.asText();
    }
}