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
import java.util.Map;

@Component
public class GroqClient {

    @Value("${groq.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectSpecSchema schema = new ProjectSpecSchema();


    public String generateCode(String modelName, String prompt) throws IOException, InterruptedException {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                },
                "max_tokens", 200,
                "temperature", 0.2
        );

        String jsonBody = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("STATUS: " + response.statusCode());
        System.out.println("BODY: " + response.body());

        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }

        return null;
    }
    public String generateProjectSpec(String modelName, String description)
            throws IOException, InterruptedException {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", new Object[]{
                        Map.of(
                                "role", "system",
                                "content", """
                    You are an expert software architect.

                    Convert the user's application description into
                    a ProjectSpec for our FastAPI code generator.

                    Follow the provided response schema exactly.

                    Make architectural decisions based on the user's
                    requirements. Do not invent unnecessary features.
                    """
                        ),
                        Map.of(
                                "role", "user",
                                "content", description
                        )
                },
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "project_spec",
                                "strict", true,
                                "schema", ProjectSpecSchema.getSchema()
                        )
                ),
                "temperature", 0.2
        );

        String jsonBody = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Groq API error: " + response.statusCode() + " - " + response.body());
        }

        // Parse the response JSON
        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isMissingNode() || choices.isEmpty()) {
            throw new IOException("No choices returned from Groq API");
        }

        String projectSpecJson = choices.get(0).path("message").path("content").asText();

        return projectSpecJson;
    }

}
