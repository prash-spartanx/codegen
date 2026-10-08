package com.prashant.codegen.util;

import com.prashant.codegen.model.EntitySpec;
import com.prashant.codegen.model.FieldSpec;
import com.prashant.codegen.model.RelationshipSpec;

public class NamingUtils {
    /**
     * Resolve the FK column field on `entity` for the given
     * many_to_one / one_to_one relationship.
     *
     * Resolution order:
     *   1. relationship field name matches a field on the entity
     *   2. <relationshipField>_id matches a field
     *   3. <targetSnakeCase>_id matches a field
     *   4. <relationshipField-prefix>_to heuristic (assignee -> assigned_to)
     *
     * Returns null if none match. The caller decides whether that is
     * an error (validator) or a skip (WARN).
     */
    public static String resolveForeignKeyField(EntitySpec entity, RelationshipSpec rel) {
        if (entity == null || entity.getFields() == null || rel == null) {
            return null;
        }
        String relationshipField = rel.getField();
        if (relationshipField == null || relationshipField.isBlank()) {
            return null;
        }

        for (FieldSpec field : entity.getFields()) {
            if (field.getName().equals(relationshipField)) {
                return field.getName();
            }
        }

        String conventionalFk = relationshipField.endsWith("_id")
                ? relationshipField
                : relationshipField + "_id";
        for (FieldSpec field : entity.getFields()) {
            if (field.getName().equals(conventionalFk)) {
                return field.getName();
            }
        }

        String target = rel.getTarget();
        if (target != null && !target.isBlank()) {
            String targetFk = toSnakeCase(target) + "_id";
            for (FieldSpec field : entity.getFields()) {
                if (field.getName().equals(targetFk)) {
                    return field.getName();
                }
            }
        }

        String normalizedRelationship = relationshipField.toLowerCase().replace("_", "");
        int prefixLength = Math.min(5, normalizedRelationship.length());
        String relationshipPrefix = normalizedRelationship.substring(0, prefixLength);

        for (FieldSpec field : entity.getFields()) {
            String fieldName = field.getName();
            if (!fieldName.endsWith("_to")) continue;
            String normalizedField = fieldName.toLowerCase().replace("_", "");
            if (normalizedField.startsWith(relationshipPrefix)) {
                return fieldName;
            }
        }

        return null;
    }
    public static String toSnakeCase(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i != 0 && text.charAt(i - 1) != '_') {
                    result.append("_");
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Central naming strategy for schema modules.
     * Examples: "TaskRouter" -> "tasks_schema", "AuthRouter" -> "auth_schema", "Project" -> "projects_schema"
     */
    public static String getSchemaModuleName(String name) {
        if (name == null || name.isEmpty()) return "schemas";
        String snake = toSnakeCase(name);
        if (snake.endsWith("_router")) {
            snake = snake.substring(0, snake.length() - 7);
        }
        if (snake.endsWith("_service")) {
            snake = snake.substring(0, snake.length() - 8);
        }
        if (!snake.endsWith("_schema")) {
            snake = snake + "_schema";
        }
        return snake;
    }
    /**
     * Router tag used for OpenAPI grouping.
     *
     * Derived from the schema module name by stripping the "_schema" suffix.
     *
     *   AuthRouter     -> auth
     *   TasksRouter    -> tasks
     *   ExpensesRouter -> expenses
     */
    public static String getRouterTag(String routerName) {
        String schema = getSchemaModuleName(routerName);
        if (schema.endsWith("_schema")) {
            return schema.substring(0, schema.length() - "_schema".length());
        }
        return schema;
    }

    /**
     * Central pluralization for table names and collection nouns.
     *
     * Rules:
     *   -y (consonant + y)      -> -ies      (Category -> categories)
     *   -s, -x, -ch, -sh        -> -es       (Box -> boxes, Class -> classes)
     *   otherwise               -> -s        (User -> users, Project -> projects)
     *
     * Input is expected to already be snake_case.
     */
    public static String plural(String snake) {
        if (snake == null || snake.isEmpty()) return snake;

        // If already ends with _id or _to etc., don't touch (defensive)
        // (Not currently needed, but cheap.)

        if (snake.endsWith("y")
                && snake.length() >= 2
                && !isVowel(snake.charAt(snake.length() - 2))) {
            return snake.substring(0, snake.length() - 1) + "ies";
        }

        if (snake.endsWith("s")
                || snake.endsWith("x")
                || snake.endsWith("ch")
                || snake.endsWith("sh")) {
            return snake + "es";
        }

        return snake + "s";
    }

    /**
     * Convenience: entity PascalCase name -> snake_case table name.
     * Example: "Category" -> "categories", "User" -> "users"
     */
    public static String toTableName(String entityName) {
        return plural(toSnakeCase(entityName));
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
}