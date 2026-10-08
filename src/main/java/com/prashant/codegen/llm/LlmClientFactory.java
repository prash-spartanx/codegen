package com.prashant.codegen.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LlmClientFactory {

    private final Map<String, LlmClient> clients;

    public LlmClientFactory(List<LlmClient> clientList) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(
                        client -> client.getProviderName().toLowerCase(),
                        Function.identity()
                ));
    }

    /**
     * Retrieves the LlmClient implementation matching the given provider name.
     *
     * @param provider e.g., "gemini", "openai", "ollama"
     * @return LlmClient instance
     */
    public LlmClient getClient(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("LLM provider name cannot be null or empty.");
        }

        LlmClient client = clients.get(provider.toLowerCase());

        if (client == null) {
            throw new IllegalArgumentException(
                    "Unsupported LLM provider: '" + provider + "'. " +
                            "Available providers: " + clients.keySet()
            );
        }

        return client;
    }
}