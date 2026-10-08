package com.prashant.codegen.schema;

import java.util.List;
import java.util.Map;

public class ProjectSpecSchema {

    public static Map<String, Object> getSchema() {

        return Map.ofEntries(
                Map.entry("type", "object"),
                Map.entry("additionalProperties", false),
                Map.entry("properties", Map.ofEntries(

                        Map.entry("name", Map.of("type", "string")),
                        Map.entry("description", Map.of("type", "string")),
                        Map.entry("pythonVersion", Map.of("type", "string")),

                        Map.entry("services", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )),

                        Map.entry("entities", arrayOf("entitySpec")),
                        Map.entry("routers", arrayOf("routerSpec")),
                        Map.entry("serviceLogics", arrayOf("serviceLogicSpec")),

                        Map.entry("jwtAuth", nullableRef("jwtAuthSpec")),
                        Map.entry("rateLimiter", nullableRef("rateLimiterSpec")),
                        Map.entry("kafka", nullableRef("kafkaSpec")),
                        Map.entry("urlShortener", nullableRef("urlShortenerSpec")),
                        Map.entry("apiGateway", nullableRef("apiGatewaySpec"))
                )),
                Map.entry("required", List.of(
                        "name",
                        "description",
                        "pythonVersion",
                        "services",
                        "entities",
                        "routers",
                        "serviceLogics",
                        "jwtAuth",
                        "rateLimiter",
                        "kafka",
                        "urlShortener",
                        "apiGateway"
                )),
                Map.entry("$defs", Map.ofEntries(

                        Map.entry("entitySpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "fields", arrayOf("fieldSpec"),
                                        "relationships", arrayOf("relationshipSpec")
                                ),
                                "required", List.of(
                                        "name",
                                        "fields",
                                        "relationships"
                                )
                        )),

                        Map.entry("fieldSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "type", Map.of(
                                                "type", "string",
                                                "enum", List.of(
                                                        "int",
                                                        "str",
                                                        "float",
                                                        "bool",
                                                        "datetime"
                                                )
                                        ),
                                        "primaryKey", Map.of("type", "boolean"),
                                        "nullable", Map.of("type", "boolean"),
                                        "unique", Map.of("type", "boolean"),
                                        "defaultValue", Map.of(
                                                "anyOf", List.of(
                                                        Map.of("type", "string"),
                                                        Map.of("type", "boolean"),
                                                        Map.of("type", "number"),
                                                        Map.of("type", "null")
                                                )
                                        ),
                                        "readOnly", Map.of("type", "boolean")
                                ),
                                "required", List.of(
                                        "name",
                                        "type",
                                        "primaryKey",
                                        "nullable",
                                        "unique",
                                        "defaultValue"
                                )
                        )),

                        Map.entry("relationshipSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "type", Map.of(
                                                "type", "string",
                                                "enum", List.of(
                                                        "one_to_one",
                                                        "one_to_many",
                                                        "many_to_one",
                                                        "many_to_many"
                                                )
                                        ),
                                        "target", Map.of("type", "string"),
                                        "field", Map.of("type", "string")
                                ),
                                "required", List.of(
                                        "type",
                                        "target",
                                        "field"
                                )
                        )),

                        Map.entry("endpointSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "method", Map.of(
                                                "type", "string",
                                                "enum", List.of(
                                                        "GET",
                                                        "POST",
                                                        "PUT",
                                                        "PATCH",
                                                        "DELETE"
                                                )
                                        ),
                                        "path", Map.of("type", "string"),
                                        "handler", Map.of("type", "string"),
                                        "requestBody", Map.of(
                                                "anyOf", List.of(
                                                        Map.of("type", "string"),
                                                        Map.of("type", "null")
                                                )
                                        ),
                                        "responseBody", Map.of(
                                                "anyOf", List.of(
                                                        Map.of("type", "string"),
                                                        Map.of("type", "null")
                                                )
                                        )
                                ),
                                "required", List.of(
                                        "method",
                                        "path",
                                        "handler",
                                        "requestBody",
                                        "responseBody"
                                )
                        )),

                        Map.entry("routerSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "prefix", Map.of("type", "string"),
                                        "authRequired", Map.of("type", "boolean"),
                                        "dependsOn", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string")
                                        ),
                                        "endpoints", arrayOf("endpointSpec")
                                ),
                                "required", List.of(
                                        "name",
                                        "prefix",
                                        "authRequired",
                                        "dependsOn",
                                        "endpoints"
                                )
                        )),

                        Map.entry("paramSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "type", Map.of("type", "string")
                                ),
                                "required", List.of(
                                        "name",
                                        "type"
                                )
                        )),

                        Map.entry("methodSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "params", arrayOf("paramSpec"),
                                        "returns", Map.of("type", "string"),
                                        "intent", Map.of("type", "string")
                                ),
                                "required", List.of(
                                        "name",
                                        "params",
                                        "returns",
                                        "intent"
                                )
                        )),

                        Map.entry("serviceLogicSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "dependsOn", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string")
                                        ),
                                        "methods", arrayOf("methodSpec")
                                ),
                                "required", List.of(
                                        "name",
                                        "dependsOn",
                                        "methods"
                                )
                        )),

                        Map.entry("jwtAuthSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "secretEnv", Map.of("type", "string"),
                                        "algorithm", Map.of(
                                                "type", "string",
                                                "enum", List.of(
                                                        "HS256",
                                                        "HS384",
                                                        "HS512"
                                                )
                                        ),
                                        "accessTokenExpirationMinutes", Map.of(
                                                "type", "integer"
                                        ),
                                        "refreshTokenExpirationDays", Map.of(
                                                "type", "integer"
                                        )
                                ),
                                "required", List.of(
                                        "secretEnv",
                                        "algorithm",
                                        "accessTokenExpirationMinutes",
                                        "refreshTokenExpirationDays"
                                )
                        )),

                        Map.entry("rateLimiterSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "backend", Map.of(
                                                "type", "string",
                                                "enum", List.of(
                                                        "redis",
                                                        "memory"
                                                )
                                        ),
                                        "defaultLimit", Map.of(
                                                "type", "integer"
                                        ),
                                        "windowSeconds", Map.of(
                                                "type", "integer"
                                        ),
                                        "perUser", Map.of(
                                                "type", "boolean"
                                        ),
                                        "redisUrlEnv", Map.of(
                                                "anyOf", List.of(
                                                        Map.of("type", "string"),
                                                        Map.of("type", "null")
                                                )
                                        )
                                ),
                                "required", List.of(
                                        "backend",
                                        "defaultLimit",
                                        "windowSeconds",
                                        "perUser",
                                        "redisUrlEnv"
                                )
                        )),

                        Map.entry("kafkaFieldSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "type", Map.of("type", "string")
                                ),
                                "required", List.of(
                                        "name",
                                        "type"
                                )
                        )),

                        Map.entry("kafkaTopicSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "partitions", Map.of("type", "integer"),
                                        "schema", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "$ref", "#/$defs/kafkaFieldSpec"
                                                )
                                        )
                                ),
                                "required", List.of(
                                        "name",
                                        "partitions",
                                        "schema"
                                )
                        )),

                        Map.entry("kafkaConsumerSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "topic", Map.of("type", "string"),
                                        "groupId", Map.of("type", "string"),
                                        "handler", Map.of("type", "string"),
                                        "intent", Map.of("type", "string")
                                ),
                                "required", List.of(
                                        "topic",
                                        "groupId",
                                        "handler",
                                        "intent"
                                )
                        )),

                        Map.entry("kafkaSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "bootstrapServersEnv", Map.of("type", "string"),
                                        "topics", arrayOf("kafkaTopicSpec"),
                                        "consumers", arrayOf("kafkaConsumerSpec")
                                ),
                                "required", List.of(
                                        "bootstrapServersEnv",
                                        "topics",
                                        "consumers"
                                )
                        )),

                        Map.entry("gatewayRouteSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "string"),
                                        "pathPrefix", Map.of("type", "string"),
                                        "upstreamUrl", Map.of("type", "string")
                                ),
                                "required", List.of(
                                        "serviceName",
                                        "pathPrefix",
                                        "upstreamUrl"
                                )
                        )),

                        Map.entry("apiGatewaySpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "routes", arrayOf("gatewayRouteSpec")
                                ),
                                "required", List.of("routes")
                        )),

                        Map.entry("urlShortenerSpec", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "baseUrl", Map.of("type", "string"),
                                        "codeLength", Map.of("type", "integer"),
                                        "expiryDays", Map.of("type", "integer"),
                                        "analytics", Map.of("type", "boolean")
                                ),
                                "required", List.of(
                                        "baseUrl",
                                        "codeLength",
                                        "expiryDays",
                                        "analytics"
                                )
                        ))
                ))
        );
    }

    private static Map<String, Object> arrayOf(String ref) {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "$ref", "#/$defs/" + ref
                )
        );
    }

    private static Map<String, Object> nullableRef(String ref) {
        return Map.of(
                "anyOf", List.of(
                        Map.of("$ref", "#/$defs/" + ref),
                        Map.of("type", "null")
                )
        );
    }
}