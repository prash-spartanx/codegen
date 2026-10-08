package com.prashant.codegen.generator;

import com.prashant.codegen.model.ProjectSpec;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DockerGenerator {

    // ================================================================
    // Dockerfile
    // ================================================================

    public String generateDockerfile(
            ProjectSpec spec,
            Configuration cfg)
            throws IOException, TemplateException {

        String version = spec.getPythonVersion();

        Template template =
                cfg.getTemplate("Dockerfile.yml.ftl");

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "pythonVersion",
                version
        );

        StringWriter out =
                new StringWriter();

        template.process(
                data,
                out
        );

        return out.toString();
    }


    // ================================================================
    // Docker Compose
    // ================================================================

    public String generateDockerCompose(
            ProjectSpec spec,
            Configuration cfg)
            throws IOException, TemplateException {

        Template template =
                cfg.getTemplate("Docker-compose.yml.ftl");

        Map<String, Object> data =
                new HashMap<>();

        // ------------------------------------------------------------
        // Project information
        // ------------------------------------------------------------

        String projectName =
                spec.getName() != null
                        ? spec.getName()
                        : "app";

        data.put(
                "projectName",
                projectName
        );

        data.put(
                "pythonVersion",
                spec.getPythonVersion()
        );


        // ------------------------------------------------------------
        // Infrastructure flags
        // ------------------------------------------------------------

        // PostgreSQL is currently always generated.
        data.put(
                "hasPostgres",
                true
        );

        data.put(
                "hasRedis",
                spec.getRateLimiter() != null
                        && "redis".equalsIgnoreCase(
                        spec.getRateLimiter().getBackend()
                )
        );

        data.put(
                "hasKafka",
                spec.getKafka() != null
        );

        data.put(
                "hasJwt",
                spec.getJwtAuth() != null
        );

        data.put(
                "hasUrlShortener",
                spec.getUrlShortener() != null
        );

        data.put(
                "hasApiGateway",
                spec.getApiGateway() != null
        );


        // ------------------------------------------------------------
        // Environment variables
        // ------------------------------------------------------------

        Map<String, String> envVars =
                buildEnvVars(spec);

        data.put(
                "envVars",
                envVars
        );


        // ------------------------------------------------------------
        // Render template
        // ------------------------------------------------------------

        StringWriter out =
                new StringWriter();

        template.process(
                data,
                out
        );

        return out.toString();
    }


    // ================================================================
    // Build environment variables
    // ================================================================

    /**
     * Builds environment variables required by generated services.
     *
     * The environment variable names come from ProjectSpec so that
     * generated application code and docker-compose.yml use exactly
     * the same names.
     *
     * Docker service names are used instead of localhost for
     * container-to-container communication.
     */
    private Map<String, String> buildEnvVars(
            ProjectSpec spec) {

        Map<String, String> envVars =
                new LinkedHashMap<>();


        // ------------------------------------------------------------
        // Redis
        // ------------------------------------------------------------

        if (spec.getRateLimiter() != null
                && "redis".equalsIgnoreCase(
                spec.getRateLimiter().getBackend())) {

            String envName =
                    spec.getRateLimiter()
                            .getRedisUrlEnv();

            if (envName != null
                    && !envName.isBlank()) {

                envVars.put(
                        envName,
                        "redis://redis:6379"
                );
            }
        }


        // ------------------------------------------------------------
        // URL shortener
        // ------------------------------------------------------------

        if (spec.getUrlShortener() != null) {

            String envName =
                    spec.getUrlShortener()
                            .getBaseUrl();

            if (envName != null
                    && !envName.isBlank()) {

                envVars.put(
                        envName,
                        "http://localhost:8000"
                );
            }
        }


        // ------------------------------------------------------------
        // Kafka
        // ------------------------------------------------------------

        if (spec.getKafka() != null) {

            String envName =
                    spec.getKafka()
                            .getBootstrapServersEnv();

            if (envName != null
                    && !envName.isBlank()) {

                envVars.put(
                        envName,
                        "kafka:9092"
                );
            }
        }


        // ------------------------------------------------------------
        // JWT
        // ------------------------------------------------------------

        if (spec.getJwtAuth() != null) {

            String secretEnv =
                    spec.getJwtAuth()
                            .getSecretEnv();

            if (secretEnv != null
                    && !secretEnv.isBlank()) {

                /*
                 * Docker Compose variable substitution.
                 *
                 * Example:
                 *
                 * JWT_SECRET=${JWT_SECRET}
                 *
                 * The actual secret should be supplied through the
                 * host environment or .env file rather than being
                 * hard-coded into the generated compose file.
                 */
                envVars.put(
                        secretEnv,
                        "${" + secretEnv + "}"
                );
            }
        }


        return envVars;
    }
}