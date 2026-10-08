package com.prashant.codegen.generator;

import com.prashant.codegen.model.ProjectSpec;
import com.prashant.codegen.model.RouterSpec;
import com.prashant.codegen.model.EndpointSpec;
import com.prashant.codegen.model.EntitySpec;
import freemarker.template.Configuration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntitySchemaGenerator {

    /**
     * Generates a map of filename (e.g., "auth_schema.py") to its generated Python source code.
     */
    public Map<String, String> generate(ProjectSpec project, Configuration cfg) {
        Map<String, String> generatedSchemas = new HashMap<>();

        // 1. Create a lookup map for entities for fast retrieval
        Map<String, EntitySpec> entityMap = new HashMap<>();
        if (project.getEntities() != null) {
            for (EntitySpec entity : project.getEntities()) {
                entityMap.put(entity.getName(), entity);
            }
        }

        // 2. Iterate through routers to determine which schemas belong in which module
        if (project.getRouters() != null) {
            for (RouterSpec router : project.getRouters()) {
                // Derive module name (e.g., "TaskRouter" -> "tasks")
                String moduleName = deriveModuleName(router.getName());
                String fileName = moduleName + "_schema.py";

                Set<String> requiredEntityNames = new HashSet<>();

                // 3. Scan endpoints for request/response bodies
                for (EndpointSpec endpoint : router.getEndpoints()) {
                    if (endpoint.getRequestBody() != null) {
                        requiredEntityNames.add(extractBaseEntity(endpoint.getRequestBody()));
                    }
                    if (endpoint.getResponseBody() != null) {
                        requiredEntityNames.add(extractBaseEntity(endpoint.getResponseBody()));
                    }
                }

                // 4. Gather the actual EntitySpec objects
                List<EntitySpec> moduleEntities = new ArrayList<>();
                for (String name : requiredEntityNames) {
                    if (entityMap.containsKey(name)) {
                        moduleEntities.add(entityMap.get(name));
                    }
                }
                String authEntityName = null;

                if (project.getEntities() != null) {
                    for (EntitySpec entity : project.getEntities()) {
                        if (entity.getFields() != null) {
                            boolean hasPassword = entity.getFields().stream()
                                    .anyMatch(field -> "password".equals(field.getName()));

                            if (hasPassword) {
                                authEntityName = entity.getName();
                                break;
                            }
                        }
                    }
                }

                // 5. Generate the file if there are schemas to build
                if (!moduleEntities.isEmpty()) {
                    try {
                        var template = cfg.getTemplate("entity_schema.py.ftl");
                        var dataModel = new HashMap<String, Object>();
                        // Inside SchemaGenerator.java generate() method:
                        boolean isAuthModule = fileName.equals("auth_schema.py") || moduleName.equals("auth");

                        dataModel.put("entities", moduleEntities);
                        dataModel.put("hasJwtAuth", project.getJwtAuth() != null);
                        dataModel.put("isAuthModule", isAuthModule);
                        dataModel.put("authEntityName", authEntityName);

                        java.io.StringWriter out = new java.io.StringWriter();
                        template.process(dataModel, out);
                        generatedSchemas.put(fileName, out.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return generatedSchemas;
    }

    /**
     * Converts "TaskRouter" or "Tasks" into "tasks"
     */
    private String deriveModuleName(String routerName) {
        return routerName.toLowerCase().replace("router", "");
    }

    /**
     * Strips List[], Create, Update, Response wrappers to find the base entity.
     * E.g., "List[TaskResponse]" -> "Task"
     */
    private String extractBaseEntity(String modelName) {
        String base = modelName;
        // Handle List wrapper
        Matcher listMatcher = Pattern.compile("List\\[(.*?)\\]").matcher(base);
        if (listMatcher.find()) {
            base = listMatcher.group(1);
        }
        // Strip suffixes
        return base.replace("Create", "")
                .replace("Update", "")
                .replace("Response", "")
                .replace("List", "");
    }
}