package com.prashant.codegen.llm;

import java.io.IOException;

public interface LlmClient {

    /**
     * Unique identifier for the provider (e.g., "gemini", "openai", "ollama")
     */
    String getProviderName();

    /**
     * Generates raw code or logic string.
     */
    String generateCode(String modelName, String prompt) throws IOException, InterruptedException;

    /**
     * Generates structured project specification JSON.
     */
    String generateProjectSpec(String modelName, String description) throws IOException, InterruptedException;
}