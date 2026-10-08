package com.prashant.codegen.validation;

import java.util.List;
import java.util.Map;

public class ProjectSpecValidator {

    private ProjectSpecValidator() {
    }

    public static void validate(Map<String, Object> raw) {

        if (raw == null) {
            throw new IllegalArgumentException("ProjectSpec cannot be null");
        }

        validateRequiredString(raw, "name");
        validateRequiredString(raw, "description");
        validateRequiredString(raw, "pythonVersion");

        validateStringList(raw, "services");
        validateMapList(raw, "entities");
        validateMapList(raw, "routers");
        validateMapList(raw, "serviceLogics");

        validateNullableMap(raw, "jwtAuth");
        validateNullableMap(raw, "rateLimiter");
        validateNullableMap(raw, "kafka");
        validateNullableMap(raw, "urlShortener");
        validateNullableMap(raw, "apiGateway");
    }

    private static void validateRequiredString(
            Map<String, Object> raw,
            String key
    ) {
        Object value = raw.get(key);

        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new IllegalArgumentException(
                    "ProjectSpec field '" + key + "' must be a non-empty String"
            );
        }
    }

    private static void validateStringList(
            Map<String, Object> raw,
            String key
    ) {
        Object value = raw.get(key);

        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "ProjectSpec field '" + key + "' must be a List"
            );
        }

        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(
                        "ProjectSpec field '" + key +
                                "' must contain only Strings"
                );
            }
        }
    }

    private static void validateMapList(
            Map<String, Object> raw,
            String key
    ) {
        Object value = raw.get(key);

        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "ProjectSpec field '" + key + "' must be a List"
            );
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                        "ProjectSpec field '" + key +
                                "' must contain only Objects"
                );
            }
        }
    }

    private static void validateNullableMap(
            Map<String, Object> raw,
            String key
    ) {
        Object value = raw.get(key);

        if (value != null && !(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "ProjectSpec field '" + key +
                            "' must be an Object or null"
            );
        }
    }
}