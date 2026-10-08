package com.prashant.codegen.architect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.prashant.codegen.llm.GroqClient;
import com.prashant.codegen.llm.OllamaClient;
import com.prashant.codegen.util.TextCleaningUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

public class GroqArchitect implements LlmArchitect{
    private final GroqClient client;
    private final ObjectMapper mapper ;

    public GroqArchitect(GroqClient client, ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = client;
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
    public Map<String,Object> generateYaml(String modelName , String description) throws IOException, InterruptedException {
        // ASSUMPTION: OllamaClient.generate(String) sends prompt, returns raw "response" text.
        // If OllamaClient's real signature differs (e.g. separate system/user params, or a
        // request object), adjust this call — don't change the prompt-composition logic below.

        String projectSpecJson = client.generateProjectSpec(modelName, description);

        Map<String, Object> projectSpecMap =
                mapper.readValue(projectSpecJson, new TypeReference<Map<String, Object>>() {});

        return projectSpecMap;
    }
}
