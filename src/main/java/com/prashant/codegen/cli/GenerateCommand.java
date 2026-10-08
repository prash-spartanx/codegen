package com.prashant.codegen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.codegen.architect.GeminiArchitect;
import com.prashant.codegen.architect.LlmArchitect;
import com.prashant.codegen.llm.LlmClient;
import com.prashant.codegen.llm.LlmClientFactory;
import com.prashant.codegen.mapper.ProjectSpecMapper;
import com.prashant.codegen.model.ProjectSpec;
import com.prashant.codegen.orchestrator.ProjectGenerator;
import com.prashant.codegen.yaml.YamlLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Map;

@Component
@Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        description = "Generates a FastAPI project from a description or YAML specification"
)
public class GenerateCommand implements Runnable {

    private final LlmClientFactory clientFactory;
    private final ObjectMapper mapper;
    private final ProjectSpecMapper projectSpecMapper;
    private final ProjectGenerator projectGenerator;
    private final YamlLoader yamlLoader;

    @Autowired
    public GenerateCommand(
            LlmClientFactory clientFactory,
            ObjectMapper mapper,
            ProjectSpecMapper projectSpecMapper,
            ProjectGenerator projectGenerator,
            YamlLoader yamlLoader
    ) {
        this.clientFactory = clientFactory;
        this.mapper = mapper;
        this.projectSpecMapper = projectSpecMapper;
        this.projectGenerator = projectGenerator;
        this.yamlLoader = yamlLoader;
    }

    @Option(
            names = {"-d", "--description"},
            description = "Project description to feed into the LLM"
    )
    private String description;

    @Option(
            names = {"-y", "--yaml"},
            description = "Path to an existing YAML project specification"
    )
    private String yamlFilePath;

    @Option(
            names = {"-p", "--provider"},
            defaultValue = "gemini",
            description = "LLM provider to use (gemini, openai, ollama, etc.)"
    )
    private String provider;

    @Option(
            names = {"-m", "--model"},
            defaultValue = "gemini-3.8-flash",
            description = "Model name to use when generating from a description"
    )
    private String modelName;

    @Option(
            names = {"-o", "--output"},
            defaultValue = "./generated-project",
            description = "Output directory"
    )
    private String outputRoot;

    @Override
    public void run() {

        try {

            /*
             * Resolve LLM Client dynamically based on user selection
             */
            LlmClient client = clientFactory.getClient(provider);

            /*
             * ============================================================
             * MODE 1: Generate from existing YAML
             * ============================================================
             */
            if (yamlFilePath != null && !yamlFilePath.isBlank()) {

                File yamlFile = new File(yamlFilePath);

                if (!yamlFile.exists()) {
                    throw new IllegalArgumentException(
                            "YAML file does not exist: "
                                    + yamlFile.getAbsolutePath()
                    );
                }

                if (!yamlFile.isFile()) {
                    throw new IllegalArgumentException(
                            "YAML path is not a file: "
                                    + yamlFile.getAbsolutePath()
                    );
                }

                System.out.println("📄 Loading YAML specification:");
                System.out.println("   " + yamlFile.getAbsolutePath());

                Map<String, Object> raw = yamlLoader.loadFromFile(yamlFile.getAbsolutePath());

                ProjectSpec projectSpec = projectSpecMapper.map(raw);

                System.out.println("✅ YAML successfully mapped to ProjectSpec" + " | Project name: " );

                // Pass client and modelName down to generator
               projectGenerator.generate(projectSpec, outputRoot,provider, modelName);

                System.out.println("✅ Generation complete!");
                System.out.println("📁 Output written to: " + new File(outputRoot).getAbsolutePath());

                return;
            }

            /*
             * ============================================================
             * MODE 2: Generate from natural-language description
             * ============================================================
             */
            if (description != null && !description.isBlank()) {

                System.out.println("🤖 Generating project specification...");
                System.out.println("   Provider: " + provider + " | Model: " + modelName);

                LlmArchitect architect = new GeminiArchitect(client, mapper);

                Map<String, Object> raw = architect.generateYaml(modelName, description);

                ProjectSpec projectSpec = projectSpecMapper.map(raw);

                // Pass client and modelName down to generator
                projectGenerator.generate(projectSpec, outputRoot, provider, modelName);

                System.out.println("✅ Generation complete!");
                System.out.println("📁 Output written to: " + new File(outputRoot).getAbsolutePath());

                return;
            }

            /*
             * Neither YAML nor description was supplied.
             */
            throw new IllegalArgumentException(
                    "You must provide either --yaml <path> or --description <description>"
            );

        } catch (Exception e) {

            System.err.println("❌ Generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}