package com.prashant.codegen.orchestrator;

import com.prashant.codegen.generator.*;
import com.prashant.codegen.model.EntitySpec;
import com.prashant.codegen.model.ProjectSpec;
import com.prashant.codegen.model.RouterSpec;
import com.prashant.codegen.model.ServiceLogicSpec;
import com.prashant.codegen.util.NamingUtils;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProjectGenerator {

    private final EntityModelGenerator entityModelGenerator;
    private final EntitySchemaGenerator entitySchemaGenerator;
    private final RouterGenerator routerGenerator;
    private final ServiceLogicGenerator serviceLogicGenerator;
    private final FileWriter fileWriter;
    private final Configuration freemarkerConfig;
    private final MainGenerator mainGenerator;
    private final JwtAuthGenerator jwtAuthGenerator;
    private final KafkaGenerator kafkaGenerator;
    private final RateLimiterGenerator rateLimiterGenerator;
    private final UrlShortnerGenerator urlShortenerGenerator;
    private final ApiGatewayGenerator apiGatewayGenerator;
    private final DockerGenerator dockerGenerator;

    // Keeps track of everything that failed during generation.
    private final List<String> errors = new ArrayList<>();

    public ProjectGenerator(
            EntityModelGenerator entityModelGenerator,
            EntitySchemaGenerator entitySchemaGenerator,
            RouterGenerator routerGenerator,
            ServiceLogicGenerator serviceLogicGenerator,
            FileWriter fileWriter,
            Configuration freemarkerConfig,
            MainGenerator mainGenerator,
            JwtAuthGenerator jwtAuthGenerator,
            KafkaGenerator kafkaGenerator,
            RateLimiterGenerator rateLimiterGenerator,
            UrlShortnerGenerator urlShortenerGenerator,
            ApiGatewayGenerator apiGatewayGenerator,
            DockerGenerator dockerGenerator) {

        this.entityModelGenerator = entityModelGenerator;
        this.entitySchemaGenerator = entitySchemaGenerator;
        this.routerGenerator = routerGenerator;
        this.serviceLogicGenerator = serviceLogicGenerator;
        this.fileWriter = fileWriter;
        this.freemarkerConfig = freemarkerConfig;
        this.mainGenerator = mainGenerator;
        this.jwtAuthGenerator = jwtAuthGenerator;
        this.kafkaGenerator = kafkaGenerator;
        this.rateLimiterGenerator = rateLimiterGenerator;
        this.urlShortenerGenerator = urlShortenerGenerator;
        this.apiGatewayGenerator = apiGatewayGenerator;
        this.dockerGenerator = dockerGenerator;
    }

    public void generate(ProjectSpec projectSpec, String outputRoot ,String provider ,String modelName)
            throws IOException, TemplateException {

        errors.clear();

        System.out.println("=================================");
        System.out.println("Starting project generation...");
        System.out.println("Output directory: " + outputRoot);
        System.out.println("=================================");

        // ============================================================
        // 1. Entity Models and Schemas
        // ============================================================

        List<EntitySpec> allEntities = projectSpec.getEntities();

        if (allEntities != null) {

            for (EntitySpec entitySpec : allEntities) {

                try {
                    String modelCode = entityModelGenerator.generate(
                            entitySpec,
                            allEntities,
                            freemarkerConfig
                    );

                    String modelFileName =
                            NamingUtils.toSnakeCase(entitySpec.getName()) + ".py";

                    fileWriter.WriteFile(
                            outputRoot,
                            "app/models/" + modelFileName,
                            modelCode
                    );

                    System.out.println(
                            "Generated model: " + modelFileName
                    );

                } catch (Exception e) {

                    recordError(
                            "Entity model: " + entitySpec.getName(),
                            e
                    );
                }

                try {
                    // Generate all schema modules at once using the updated generator
                    Map<String, String> schemaFiles = entitySchemaGenerator.generate(
                            projectSpec,
                            freemarkerConfig
                    );

                    // Iterate through the returned map to write each module to disk
                    for (Map.Entry<String, String> entry : schemaFiles.entrySet()) {
                        String schemaFileName = entry.getKey();
                        String schemaCode = entry.getValue();

                        fileWriter.WriteFile(
                                outputRoot,
                                "app/schemas/" + schemaFileName,
                                schemaCode
                        );

                        System.out.println(
                                "Generated schema module: " + schemaFileName
                        );
                    }

                } catch (Exception e) {
                    // Record error for the entire schema generation process
                    recordError(
                            "Schema modules generation for project: " + projectSpec.getName(),
                            e
                    );
                }
            }
        }

        // ============================================================
        // 2. Routers
        // ============================================================

        List<RouterSpec> routers = projectSpec.getRouters();

        if (routers != null) {

            for (RouterSpec routerSpec : routers) {

                try {

                    String routerCode =
                            routerGenerator.generate(
                                    routerSpec,
                                    freemarkerConfig
                            );

                    String routerFileName =
                            NamingUtils.toSnakeCase(routerSpec.getName()) + ".py";

                    fileWriter.WriteFile(
                            outputRoot,
                            "app/routers/" + routerFileName,
                            routerCode
                    );

                    System.out.println(
                            "Generated router: " + routerFileName
                    );

                } catch (Exception e) {

                    recordError(
                            "Router: " + routerSpec.getName(),
                            e
                    );
                }
            }
        }

        // ============================================================
        // 3. Service Logic
        // ============================================================

        List<ServiceLogicSpec> services =
                projectSpec.getServiceLogics();

        if (services != null) {

            for (ServiceLogicSpec serviceSpec : services) {

                try {

                    String serviceCode =
                            serviceLogicGenerator.generate(
                                    serviceSpec,
                                    projectSpec,
                                    provider,
                                    modelName
                            );

                    String serviceFileName =
                            NamingUtils.toSnakeCase(serviceSpec.getName()) + ".py";

                    fileWriter.WriteFile(
                            outputRoot,
                            "app/services/" + serviceFileName,
                            serviceCode
                    );

                    System.out.println(
                            "Generated service: " + serviceFileName
                    );

                } catch (Exception e) {

                    recordError(
                            "Service: " + serviceSpec.getName(),
                            e
                    );
                }
            }
        }

        // ============================================================
        // 4. Python package __init__.py files
        // ============================================================

        StringBuilder modelsInit = new StringBuilder();
        for (EntitySpec entity : allEntities) {
            String moduleName = NamingUtils.toSnakeCase(entity.getName());
            modelsInit.append("from app.models.")
                    .append(moduleName)
                    .append(" import ")
                    .append(entity.getName())
                    .append("\n");
        }
        safeWriteFile(
                outputRoot,
                "app/models/__init__.py",
                modelsInit.toString(),
                "models/__init__.py"
        );

        safeWriteFile(
                outputRoot,
                "app/schemas/__init__.py",
                "",
                "schemas/__init__.py"
        );

        safeWriteFile(
                outputRoot,
                "app/routers/__init__.py",
                "",
                "routers/__init__.py"
        );

        safeWriteFile(
                outputRoot,
                "app/services/__init__.py",
                "",
                "services/__init__.py"
        );

        // ============================================================
        // 5. JWT Authentication
        // ============================================================

        if (projectSpec.getJwtAuth() != null) {


            try {
                String example =
                        "# Copy this file to .env and set real values before running the application.\n"
                                + projectSpec.getJwtAuth().getSecretEnv() + "=Prashant12345678\n";
                safeWriteFile(outputRoot, ".env", example, ".env.example");
                System.out.println("Generated .env.example for JWT authentication.");
                EntitySpec authEntity = findAuthEntity(projectSpec);

                String jwtCode =
                        jwtAuthGenerator.generate(
                                projectSpec.getJwtAuth(),
                                authEntity,
                                freemarkerConfig
                        );

                fileWriter.WriteFile(
                        outputRoot,
                        "app/auth.py",
                        jwtCode
                );

                System.out.println("Generated JWT authentication.");

                String registerServiceCode =
                        jwtAuthGenerator.generateService(
                                "register_user",
                                projectSpec.getJwtAuth(),
                                authEntity,
                                freemarkerConfig
                        );
                fileWriter.WriteFile(outputRoot, "app/services/register_user.py", registerServiceCode);
                System.out.println("Generated register_user service.");

                String loginServiceCode =
                        jwtAuthGenerator.generateService(
                                "login_user",
                                projectSpec.getJwtAuth(),
                                authEntity,
                                freemarkerConfig
                        );
                fileWriter.WriteFile(outputRoot, "app/services/login_user.py", loginServiceCode);
                System.out.println("Generated login_user service.");

            } catch (Exception e) {

                recordError("JWT authentication", e);
            }
        }

//        // ============================================================
//        // 6. Kafka
//        // ============================================================
//
//        if (projectSpec.getKafka() != null) {
//
//            try {
//
//                String kafkaProducerCode =
//                        kafkaGenerator.GenerateKafkaProducer(
//                                projectSpec.getKafka(),
//                                freemarkerConfig
//                        );
//
//                fileWriter.WriteFile(
//                        outputRoot,
//                        "app/kafka_producer.py",
//                        kafkaProducerCode
//                );
//
//                System.out.println("Generated Kafka producer.");
//
//            } catch (Exception e) {
//
//                recordError("Kafka producer", e);
//            }
//
//            try {
//
//                String kafkaConsumerCode =
//                        kafkaGenerator.GenerateKafkaConsumer(
//                                projectSpec.getKafka(),
//                                freemarkerConfig
//                        );
//
//                fileWriter.WriteFile(
//                        outputRoot,
//                        "app/kafka_consumer.py",
//                        kafkaConsumerCode
//                );
//
//                System.out.println("Generated Kafka consumer.");
//
//            } catch (Exception e) {
//
//                recordError("Kafka consumer", e);
//            }
//        }

//        // ============================================================
//        // 7. Rate Limiter
//        // ============================================================
//
//        if (projectSpec.getRateLimiter() != null) {
//
//            try {
//
//                String rateLimiterCode =
//                        rateLimiterGenerator.generate(
//                                projectSpec.getRateLimiter(),
//                                freemarkerConfig
//                        );
//
//                fileWriter.WriteFile(
//                        outputRoot,
//                        "app/rate_limiter.py",
//                        rateLimiterCode
//                );
//
//                System.out.println("Generated rate limiter.");
//
//            } catch (Exception e) {
//
//                recordError("Rate limiter", e);
//            }
//        }
//
//        // ============================================================
//        // 8. URL Shortener
//        // ============================================================
//
//        if (projectSpec.getUrlShortener() != null) {
//
//            try {
//
//                String urlShortenerCode =
//                        urlShortenerGenerator.generate(
//                                projectSpec.getUrlShortener(),
//                                freemarkerConfig
//                        );
//
//                fileWriter.WriteFile(
//                        outputRoot,
//                        "app/url_shortener.py",
//                        urlShortenerCode
//                );
//
//                System.out.println("Generated URL shortener.");
//
//            } catch (Exception e) {
//
//                recordError("URL shortener", e);
//            }
//        }
//
//        // ============================================================
//        // 9. API Gateway
//        // ============================================================
//
//        if (projectSpec.getApiGateway() != null) {
//
//            try {
//
//                String apiGatewayCode =
//                        apiGatewayGenerator.generate(
//                                projectSpec.getApiGateway(),
//                                freemarkerConfig
//                        );
//
//                fileWriter.WriteFile(
//                        outputRoot,
//                        "app/api_gateway.py",
//                        apiGatewayCode
//                );
//
//                System.out.println("Generated API gateway.");
//
//            } catch (Exception e) {
//
//                recordError("API gateway", e);
//            }
//        }

        // ============================================================
        // 10. Docker
        // ============================================================

        try {

            String dockerfileCode =
                    dockerGenerator.generateDockerfile(
                            projectSpec,
                            freemarkerConfig
                    );

            fileWriter.WriteFile(
                    outputRoot,
                    "Dockerfile",
                    dockerfileCode
            );

            System.out.println("Generated Dockerfile.");

        } catch (Exception e) {

            recordError("Dockerfile", e);
        }

        try {

            String dockerComposeCode =
                    dockerGenerator.generateDockerCompose(
                            projectSpec,
                            freemarkerConfig
                    );

            fileWriter.WriteFile(
                    outputRoot,
                    "docker-compose.yml",
                    dockerComposeCode
            );

            System.out.println("Generated docker-compose.yml.");

        } catch (Exception e) {

            recordError("docker-compose.yml", e);
        }

        // ============================================================
        // 11. Main.py
        // ============================================================

        try {

            String mainCode =
                    mainGenerator.generate(
                            projectSpec,
                            freemarkerConfig
                    );

            fileWriter.WriteFile(
                    outputRoot,
                    "app/main.py",
                    mainCode
            );

            System.out.println("Generated main.py.");

        } catch (Exception e) {

            recordError("main.py", e);
        }

        // ============================================================
        // 12. Database
        // ============================================================

        try {

            writeDatabaseFile(outputRoot);

            System.out.println("Generated database.py.");

        } catch (Exception e) {

            recordError("database.py", e);
        }

        // ============================================================
        // 13. Requirements
        // ============================================================

        try {

            writeRequirementsFile(
                    outputRoot,
                    projectSpec
            );

            System.out.println("Generated requirements.txt.");

        } catch (Exception e) {

            recordError("requirements.txt", e);
        }

        // ============================================================
        // Final report
        // ============================================================

        System.out.println();
        System.out.println("=================================");
        System.out.println("Project generation completed.");
        System.out.println("=================================");

        if (errors.isEmpty()) {

            System.out.println(
                    "SUCCESS: All files generated successfully."
            );

        } else {

            System.out.println(
                    "WARNING: Generation completed with "
                            + errors.size()
                            + " error(s)."
            );

            System.out.println();

            for (String error : errors) {
                System.out.println(error);
            }
        }
    }

    // ================================================================
    // Database
    // ================================================================

    private void writeDatabaseFile(String outputRoot) throws IOException {

        String databaseCode =
                "import os\n" +
                        "from sqlalchemy import create_engine\n" +
                        "from sqlalchemy.ext.declarative import declarative_base\n" +
                        "from sqlalchemy.orm import sessionmaker\n\n" +
                        "SQLALCHEMY_DATABASE_URL = os.getenv(\"DATABASE_URL\", \"sqlite:///./test.db\")\n\n" +
                        "connect_args = {}\n" +
                        "if SQLALCHEMY_DATABASE_URL.startswith(\"sqlite\"):\n" +
                        "    connect_args[\"check_same_thread\"] = False\n\n" +
                        "engine = create_engine(SQLALCHEMY_DATABASE_URL, connect_args=connect_args)\n" +
                        "SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)\n" +
                        "Base = declarative_base()\n\n" +
                        "def get_db():\n" +
                        "    db = SessionLocal()\n" +
                        "    try:\n" +
                        "        yield db\n" +
                        "    finally:\n" +
                        "        db.close()\n";

        fileWriter.WriteFile(
                outputRoot,
                "app/database.py",
                databaseCode
        );
    }

    // ================================================================
    // Requirements
    // ================================================================
    private void writeRequirementsFile(
            String outputRoot,
            ProjectSpec projectSpec)
            throws IOException {

        StringBuilder sb = new StringBuilder();

        // Core dependencies
        sb.append("fastapi\n");
        sb.append("uvicorn\n");
        sb.append("sqlalchemy\n");
        sb.append("pydantic\n");
        sb.append("python-multipart\n");

        // PostgreSQL driver — required because docker-compose
        // sets DATABASE_URL to a postgresql:// URL and database.py
        // falls back to SQLite only when DATABASE_URL is absent.
        sb.append("psycopg[binary]\n");

        // JWT
        if (projectSpec.getJwtAuth() != null) {
            sb.append("python-jose[cryptography]\n");
            sb.append("bcrypt==4.0.1\n");
            sb.append("passlib==1.7.4\n");
        }

        // Kafka
        if (projectSpec.getKafka() != null) {
            sb.append("confluent-kafka\n");
        }

        // Rate limiter — redis only when the backend is actually redis
        if (projectSpec.getRateLimiter() != null
                && "redis".equalsIgnoreCase(
                projectSpec.getRateLimiter().getBackend())) {
            sb.append("redis\n");
        }

        // API Gateway
        if (projectSpec.getApiGateway() != null) {
            sb.append("httpx\n");
        }

        // Environment variables
        sb.append("python-dotenv\n");

        fileWriter.WriteFile(
                outputRoot,
                "requirements.txt",
                sb.toString()
        );
    }

    // ================================================================
    // Safe file writer
    // ================================================================

    private void safeWriteFile(
            String outputRoot,
            String relativePath,
            String content,
            String description) {

        try {

            fileWriter.WriteFile(
                    outputRoot,
                    relativePath,
                    content
            );

            System.out.println(
                    "Generated " + description + "."
            );

        } catch (Exception e) {

            recordError(description, e);
        }
    }

    // jwt authentity helper
    private EntitySpec findAuthEntity(ProjectSpec projectSpec) {
        if (projectSpec.getEntities() == null) return null;
        for (EntitySpec e : projectSpec.getEntities()) {
            if (e.getFields() == null) continue;
            boolean hasPassword = e.getFields().stream()
                    .anyMatch(f -> "password".equals(f.getName()));
            if (hasPassword) return e;
        }
        return null;
    }

    // ================================================================
    // Error handling
    // ================================================================

    private void recordError(
            String component,
            Exception e) {

        String message =
                "FAILED: " +
                        component +
                        " -> " +
                        e.getClass().getSimpleName() +
                        ": " +
                        e.getMessage();

        errors.add(message);

        System.err.println(message);

        // Very useful during development.
        e.printStackTrace();
    }
}