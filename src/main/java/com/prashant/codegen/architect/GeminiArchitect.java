package com.prashant.codegen.architect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.codegen.llm.LlmClient;

import java.io.IOException;
import java.util.Map;

public class GeminiArchitect implements LlmArchitect {

    private final LlmClient client;
    private final ObjectMapper mapper;

    public GeminiArchitect(LlmClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    private String loadSystemPrompt() {
        try (var in = getClass().getResourceAsStream("/prompts/architect_system_prompt.txt")) {
            if (in == null) {
                throw new IllegalStateException("architect_system_prompt.txt not found on classpath");
            }
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load architect system prompt", e);
        }
    }

    @Override
    public Map<String, Object> generateYaml(String modelName, String description) throws IOException, InterruptedException {
        // Delegates to whatever LlmClient implementation was injected at runtime
        String projectSpecJson = client.generateProjectSpec(modelName, description);

        Map<String, Object> projectSpecMap =
                mapper.readValue(projectSpecJson, new TypeReference<Map<String, Object>>() {});

        return projectSpecMap;
    }
}