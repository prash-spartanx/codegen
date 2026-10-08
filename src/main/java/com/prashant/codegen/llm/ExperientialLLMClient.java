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
import java.time.Duration;
import java.util.Map;

@Component
public class ExperientialLLMClient {

    @Value("${experiential.api.key}")
    private String apiKey;

    @Value("${experiential.base-url:https://api.experientiallabs.ai/v1}")
    private String baseUrl;

    private final HttpClient httpClient;

    private final ObjectMapper mapper;

    public ExperientialLLMClient() {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.mapper = new ObjectMapper();
    }

    /**
     * Generic code generation call.
     *
     * Use this when you simply want text/code from the model.
     */
    public String generateCode(
            String modelName,
            String prompt
    ) throws IOException, InterruptedException {

        String url = baseUrl + "/chat/completions";

        Map<String, Object> requestBody = Map.of(
                "model", modelName,

                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                },

                "max_tokens", 8000
        );

        return sendRequest(url, requestBody);
    }


    /**
     * Converts a natural-language application description
     * into the ProjectSpec JSON required by the generator.
     *
     * The JSON Schema is sent to the model through
     * response_format.
     */
    public String generateProjectSpec(
            String modelName,
            String description
    ) throws IOException, InterruptedException {

        String url = baseUrl + "/chat/completions";

        Map<String, Object> requestBody = Map.of(

                "model", modelName,

                "messages", new Object[]{

                        Map.of(
                                "role", "system",
                                "content", """
                                        You are an expert software architect.

                                        Your job is to convert the user's
                                        application description into a ProjectSpec
                                        for a FastAPI code generator.

                                        Follow the provided JSON Schema exactly.

                                        Important rules:

                                        1. Return ONLY data that conforms to the schema.
                                        2. Do not add fields that are not in the schema.
                                        3. Do not remove required fields.
                                        4. Make sensible architectural decisions
                                           based on the user's requirements.
                                        5. Do not invent unnecessary features.
                                        6. If a feature is not requested, keep its
                                           corresponding configuration disabled or empty
                                           according to the schema.
                                        7. Relationships must reference actual entities.
                                        8. Router endpoints must correspond to the
                                           application's entities and functionality.
                                        9. Service logic should contain only logic
                                           actually required by the application.
                                        10. Use valid values for all enum fields.
                                        """
                        ),

                        Map.of(
                                "role", "user",
                                "content", description
                        )
                },

                /*
                 * This is the important part.
                 *
                 * The model receives the exact structure that Java expects.
                 */
                "response_format", Map.of(

                        "type", "json_schema",

                        "json_schema", Map.of(

                                "name", "project_spec",

                                "strict", true,

                                "schema", ProjectSpecSchema.getSchema()
                        )
                ),

                /*
                 * Keep generation deterministic.
                 *
                 * IMPORTANT:
                 * We intentionally do NOT send temperature here.
                 *
                 * Some newer reasoning models don't accept the traditional
                 * temperature parameter.
                 */
                "max_tokens", 16000
        );

        return sendRequest(url, requestBody);
    }


    /**
     * Sends the HTTP request to Experiential Labs.
     */
    private String sendRequest(
            String url,
            Map<String, Object> requestBody
    ) throws IOException, InterruptedException {

        String jsonBody = mapper.writeValueAsString(requestBody);

        System.out.println("==========================================");
        System.out.println("EXPERIENTIAL LLM REQUEST");
        System.out.println("URL   : " + url);
        System.out.println("MODEL : " + requestBody.get("model"));
        System.out.println("==========================================");

        HttpRequest request = HttpRequest.newBuilder()

                .uri(URI.create(url))

                .timeout(Duration.ofMinutes(5))

                .header(
                        "Content-Type",
                        "application/json"
                )

                .header(
                        "Authorization",
                        "Bearer " + apiKey
                )

                .POST(
                        HttpRequest.BodyPublishers.ofString(jsonBody)
                )

                .build();


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );


        System.out.println("==========================================");
        System.out.println("EXPERIENTIAL LLM RESPONSE");
        System.out.println("STATUS : " + response.statusCode());
        System.out.println("==========================================");

        /*
         * Always print the body while developing.
         *
         * This makes API errors much easier to diagnose.
         */
        System.out.println(response.body());

        System.out.println("==========================================");


        /*
         * Anything other than 2xx is an API failure.
         */
        if (response.statusCode() < 200 ||
                response.statusCode() >= 300) {

            throw new IOException(
                    "Experiential Labs API error. " +
                            "HTTP " + response.statusCode() +
                            ": " + response.body()
            );
        }


        JsonNode root;

        try {

            root = mapper.readTree(response.body());

        } catch (Exception e) {

            throw new IOException(
                    "Experiential Labs returned invalid JSON: "
                            + response.body(),
                    e
            );
        }


        /*
         * OpenAI-compatible response:
         *
         * {
         *   "choices": [
         *     {
         *       "message": {
         *         "content": "..."
         *       }
         *     }
         *   ]
         * }
         */

        JsonNode choices = root.path("choices");


        if (!choices.isArray() ||
                choices.isEmpty()) {

            throw new IOException(
                    "Experiential Labs returned no choices. Response: "
                            + response.body()
            );
        }


        JsonNode message =
                choices.get(0).path("message");


        if (message.isMissingNode()) {

            throw new IOException(
                    "Experiential Labs response contains no message. Response: "
                            + response.body()
            );
        }


        JsonNode content =
                message.path("content");


        if (content.isMissingNode() ||
                content.isNull()) {

            throw new IOException(
                    "Experiential Labs response contains no content. Response: "
                            + response.body()
            );
        }


        String result = content.asText();


        if (result == null ||
                result.isBlank()) {

            throw new IOException(
                    "Experiential Labs returned empty content."
            );
        }


        return result;
    }


    /**
     * Optional helper:
     *
     * Fetch the models available to your Experiential Labs API key.
     *
     * This is useful for checking the exact model names/slugs.
     */
    public String getAvailableModels()
            throws IOException, InterruptedException {

        String url = baseUrl + "/models";


        HttpRequest request =
                HttpRequest.newBuilder()

                        .uri(URI.create(url))

                        .timeout(Duration.ofSeconds(30))

                        .header(
                                "Authorization",
                                "Bearer " + apiKey
                        )

                        .GET()

                        .build();


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );


        if (response.statusCode() < 200 ||
                response.statusCode() >= 300) {

            throw new IOException(
                    "Failed to retrieve Experiential Labs models. " +
                            "HTTP " + response.statusCode() +
                            ": " + response.body()
            );
        }


        return response.body();
    }
}