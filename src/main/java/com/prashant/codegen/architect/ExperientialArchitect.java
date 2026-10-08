package com.prashant.codegen.architect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.codegen.llm.ExperientialLLMClient;

import java.io.IOException;
import java.util.Map;

public class ExperientialArchitect implements LlmArchitect {

    private final ExperientialLLMClient client;
    private final ObjectMapper mapper;

    public ExperientialArchitect(
            ExperientialLLMClient client,
            ObjectMapper mapper
    ) {
        this.client = client;
        this.mapper = mapper;
    }

    private String loadSystemPrompt() {

        try (var in = getClass()
                .getResourceAsStream("/prompts/architect_system_prompt.txt")) {

            if (in == null) {
                throw new IllegalStateException(
                        "architect_system_prompt.txt not found on classpath"
                );
            }

            return new String(in.readAllBytes());

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to load architect system prompt",
                    e
            );
        }
    }

    @Override
    public Map<String, Object> generateYaml(
            String modelName,
            String description
    ) throws IOException, InterruptedException {

        /*
         * ExperientialLLMClient already handles:
         *
         * description
         *       ↓
         * ProjectSpec JSON Schema
         *       ↓
         * LLM
         *       ↓
         * structured JSON
         *
         * So this class only needs to receive the JSON
         * and convert it into the Map expected by the
         * existing architecture layer.
         */

        String projectSpecJson =
                client.generateProjectSpec(
                        modelName,
                        description
                );

        /*
         * Convert the returned JSON into the generic
         * Map expected by LlmArchitect.
         */
        return mapper.readValue(
                projectSpecJson,
                new TypeReference<Map<String, Object>>() {}
        );
    }
}