package com.prashant.codegen.util;

import com.prashant.codegen.model.EndpointSpec;
import com.prashant.codegen.model.ProjectSpec;
import com.prashant.codegen.model.RouterSpec;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchemaRegistry {

    /**
     * Scans all routers in the project and maps every request/response type to its target schema module.
     */
    public static Map<String, String> buildTypeToSchemaModuleMap(ProjectSpec projectSpec) {
        Map<String, String> map = new HashMap<>();
        if (projectSpec == null || projectSpec.getRouters() == null) {
            return map;
        }

        for (RouterSpec router : projectSpec.getRouters()) {
            String schemaModule = NamingUtils.getSchemaModuleName(router.getName());

            if (router.getEndpoints() != null) {
                for (EndpointSpec endpoint : router.getEndpoints()) {
                    registerType(map, endpoint.getRequestBody(), schemaModule);
                    registerType(map, endpoint.getResponseBody(), schemaModule);
                }
            }
        }

        // Register default Auth schemas under auth_schema
        map.put("UserLogin", "auth_schema");
        map.put("TokenResponse", "auth_schema");
        map.put("TokenData", "auth_schema");

        return map;
    }

    private static void registerType(Map<String, String> map, String typeStr, String schemaModule) {
        if (typeStr == null || typeStr.isEmpty()) return;

        String bare = typeStr;
        Matcher listMatcher = Pattern.compile("(?:List|Optional)\\[(.*?)\\]").matcher(bare);
        if (listMatcher.find()) {
            bare = listMatcher.group(1);
        }

        map.put(bare, schemaModule);

        // Map base entity variations (e.g. Task -> TaskCreate, TaskUpdate, etc.)
        String base = bare.replace("Create", "")
                .replace("Update", "")
                .replace("Response", "")
                .replace("Request", "")
                .replace("List", "");
        if (!base.isEmpty()) {
            map.put(base, schemaModule);
            map.put(base + "Create", schemaModule);
            map.put(base + "Update", schemaModule);
            map.put(base + "Response", schemaModule);
            map.put(base + "ListResponse", schemaModule);
        }
    }
}