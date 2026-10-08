package com.prashant.codegen.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.codegen.generator.*;
import com.prashant.codegen.llm.LlmClientFactory;
import com.prashant.codegen.llm.LlmOutputCleaner;
import com.prashant.codegen.llm.PromptBuilder;
import com.prashant.codegen.mapper.ProjectSpecMapper;
import com.prashant.codegen.orchestrator.FileWriter;
import com.prashant.codegen.orchestrator.ProjectGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GeneratorConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ProjectSpecMapper projectSpecMapper() {
        return new ProjectSpecMapper();
    }

    @Bean
    public EntityModelGenerator entityModelGenerator() {
        return new EntityModelGenerator();
    }

    @Bean
    public EntitySchemaGenerator entitySchemaGenerator() {
        return new EntitySchemaGenerator();
    }

    @Bean
    public RouterGenerator routerGenerator() {
        return new RouterGenerator();
    }

    @Bean
    public MainGenerator mainGenerator() {
        return new MainGenerator();
    }

    @Bean
    public JwtAuthGenerator jwtAuthGenerator() {
        return new JwtAuthGenerator();
    }

    @Bean
    public FileWriter fileWriter() {
        return new FileWriter();
    }

    @Bean
    public KafkaGenerator kafkaGenerator() {
        return new KafkaGenerator();
    }

    @Bean
    public RateLimiterGenerator rateLimiterGenerator() {
        return new RateLimiterGenerator();
    }

    @Bean
    public UrlShortnerGenerator urlShortnerGenerator() {
        return new UrlShortnerGenerator();
    }

    @Bean
    public ApiGatewayGenerator apiGatewayGenerator() {
        return new ApiGatewayGenerator();
    }

    @Bean
    public DockerGenerator dockerGenerator() {
        return new DockerGenerator();
    }

    @Bean
    public freemarker.template.Configuration freemarkerConfig()
            throws IOException {

        return FreeMarkerConfig.build();
    }

    /*
     * Service Logic Generator
     *
     * Injects LlmClientFactory to dynamically resolve clients at runtime
     * based on CLI execution options.
     */
    @Bean
    public ServiceLogicGenerator serviceLogicGenerator(
            freemarker.template.Configuration freemarkerConfig,
            LlmClientFactory clientFactory,
            PromptBuilder promptBuilder,
            LlmOutputCleaner outputCleaner) {

        return new ServiceLogicGenerator(
                freemarkerConfig,
                clientFactory,
                promptBuilder,
                outputCleaner
        );
    }

    @Bean
    public PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }

    @Bean
    public LlmOutputCleaner llmOutputCleaner() {
        return new LlmOutputCleaner();
    }

    @Bean
    public ProjectGenerator projectGenerator(
            EntityModelGenerator entityModelGen,
            EntitySchemaGenerator entitySchemaGen,
            RouterGenerator routerGen,
            ServiceLogicGenerator serviceGen,
            FileWriter fileWriter,
            freemarker.template.Configuration freemarkerConfig,
            MainGenerator mainGenerator,
            JwtAuthGenerator jwtAuthGenerator,
            KafkaGenerator kafkaGenerator,
            RateLimiterGenerator rateLimiterGenerator,
            UrlShortnerGenerator urlShortnerGenerator,
            ApiGatewayGenerator apiGatewayGenerator,
            DockerGenerator dockerGenerator) {

        return new ProjectGenerator(
                entityModelGen,
                entitySchemaGen,
                routerGen,
                serviceGen,
                fileWriter,
                freemarkerConfig,
                mainGenerator,
                jwtAuthGenerator,
                kafkaGenerator,
                rateLimiterGenerator,
                urlShortnerGenerator,
                apiGatewayGenerator,
                dockerGenerator
        );
    }
}