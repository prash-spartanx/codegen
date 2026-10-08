package com.prashant.codegen.mapper;

import com.prashant.codegen.model.*;
import org.springframework.stereotype.Component;
import static com.prashant.codegen.mapper.MapUtils.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
public class ProjectSpecMapper {

    public ProjectSpec map(Map<String, Object> raw) {
        ProjectSpec spec = new ProjectSpec();

        spec.setName(getString(raw, "name", null));
        spec.setDescription(getString(raw, "description", null));
        spec.setPythonVersion(getString(raw, "pythonVersion", null));
        spec.setServices(getStringList(raw, "services"));  // may be null

        // Entities
        List<Map<String, Object>> rawEntities = getMapList(raw, "entities");
        if (rawEntities != null) {
            spec.setEntities(mapEntities(rawEntities));
        }

        // Routers
        List<Map<String, Object>> rawRouters = getMapList(raw, "routers");
        if (rawRouters != null) {
            spec.setRouters(mapRouter(rawRouters));
        }

        // Service logics
        List<Map<String, Object>> rawServiceLogics = getMapList(raw, "serviceLogics");
        if (rawServiceLogics != null) {
            spec.setServiceLogics(mapServiceLogic(rawServiceLogics));
        }

        // JwtAuth
        Map<String, Object> rawJwtAuth = getMap(raw, "jwtAuth");
        if (rawJwtAuth != null) {
            spec.setJwtAuth(mapJwtAuth(rawJwtAuth));
        }

        // RateLimiter
        Map<String, Object> rawRateLimiter = getMap(raw, "rateLimiter");
        if (rawRateLimiter != null) {
            spec.setRateLimiter(mapRateLimiter(rawRateLimiter));
        }

        // Kafka
        Map<String, Object> rawKafka = getMap(raw, "kafka");
        if (rawKafka != null) {
            spec.setKafka(mapKafka(rawKafka));
        }

        // UrlShortener
        Map<String, Object> rawUrlShortener = getMap(raw, "urlShortener");
        if (rawUrlShortener != null) {
            spec.setUrlShortener(mapUrlShortener(rawUrlShortener));
        }

        // ApiGateway
        Map<String, Object> rawApiGateway = getMap(raw, "apiGateway");
        if (rawApiGateway != null) {
            spec.setApiGateway(mapApiGateway(rawApiGateway));
        }

        return spec;
    }

    private List<EntitySpec> mapEntities(List<Map<String, Object>> rawEntities) {
        List<EntitySpec> res = new ArrayList<>();
        for (Map<String, Object> entityMap : rawEntities) {
            EntitySpec entity = new EntitySpec();
            entity.setName(getString(entityMap, "name", null));

            List<Map<String, Object>> rawFields = getMapList(entityMap, "fields");
            entity.setFields(mapFields(rawFields));

            List<Map<String, Object>> rawRelationships = getMapList(entityMap, "relationships");
            entity.setRelationships(mapRelationships(rawRelationships));

            res.add(entity);
        }
        return res;
    }

    private List<FieldSpec> mapFields(List<Map<String, Object>> rawFields) {
        List<FieldSpec> fields = new ArrayList<>();
        if (rawFields != null) {
            for (Map<String, Object> fieldMap : rawFields) {
                FieldSpec field = new FieldSpec();
                field.setName(getString(fieldMap, "name", null));
                field.setType(getString(fieldMap, "type", null));
                field.setPrimaryKey(getBoolean(fieldMap, "primaryKey", false));
                field.setNullable(getBoolean(fieldMap, "nullable", false));
                field.setUnique(getBoolean(fieldMap, "unique", false));
                field.setDefaultValue(getString(fieldMap, "defaultValue", null));
                field.setReadOnly(getBoolean(fieldMap, "readOnly", false));
                fields.add(field);
            }
        }
        return fields;
    }

    private List<RelationshipSpec> mapRelationships(List<Map<String, Object>> rawRelationships) {
        List<RelationshipSpec> relationships = new ArrayList<>();
        if (rawRelationships != null) {
            for (Map<String, Object> relMap : rawRelationships) {
                RelationshipSpec rel = new RelationshipSpec();
                rel.setType(getString(relMap, "type", null));
                rel.setTarget(getString(relMap, "target", null));
                rel.setField(getString(relMap, "field", null));
                relationships.add(rel);
            }
        }
        return relationships;
    }

    private List<RouterSpec> mapRouter(List<Map<String, Object>> rawRouters) {
        List<RouterSpec> res = new ArrayList<>();
        if (rawRouters != null) {
            for (Map<String, Object> routerMap : rawRouters) {
                RouterSpec router = new RouterSpec();
                router.setName(getString(routerMap, "name", null));
                router.setPrefix(getString(routerMap, "prefix", null));
                router.setAuthRequired(getBoolean(routerMap, "authRequired", false));
                router.setDependsOn(getStringList(routerMap, "dependsOn"));

                List<Map<String, Object>> rawEndpoints = getMapList(routerMap, "endpoints");
                router.setEndpoints(mapEndpoint(rawEndpoints));
                res.add(router);
            }
        }
        return res;
    }

    private JwtAuthSpec mapJwtAuth(Map<String, Object> rawJwtAuth) {
        JwtAuthSpec jwtAuth = new JwtAuthSpec();
        jwtAuth.setSecretEnv(getString(rawJwtAuth, "secretEnv", null));
        jwtAuth.setAlgorithm(getString(rawJwtAuth, "algorithm", null));
        jwtAuth.setAccessTokenExpirationMinutes(getInt(rawJwtAuth, "accessTokenExpirationMinutes", 0));
        jwtAuth.setRefreshTokenExpirationDays(getInt(rawJwtAuth, "refreshTokenExpirationDays", 0));
        return jwtAuth;
    }

    private RateLimiterSpec mapRateLimiter(Map<String, Object> rawRateLimiter) {
        RateLimiterSpec rateLimiter = new RateLimiterSpec();
        rateLimiter.setBackend(getString(rawRateLimiter, "backend", null));
        rateLimiter.setDefaultLimit(getInt(rawRateLimiter, "defaultLimit", 0));
        rateLimiter.setWindowSeconds(getInt(rawRateLimiter, "windowSeconds", 0));
        rateLimiter.setPerUser(getBoolean(rawRateLimiter, "perUser", false));
        rateLimiter.setRedisUrlEnv(getString(rawRateLimiter, "redisUrlEnv", null));
        return rateLimiter;
    }

    private List<ServiceLogicSpec> mapServiceLogic(List<Map<String, Object>> rawServiceLogic) {
        List<ServiceLogicSpec> res = new ArrayList<>();
        if (rawServiceLogic != null) {
            for (Map<String, Object> logicMap : rawServiceLogic) {
                ServiceLogicSpec logic = new ServiceLogicSpec();
                logic.setName(getString(logicMap, "name", null));

                List<Map<String, Object>> rawMethods = getMapList(logicMap, "methods");
                logic.setMethods(mapMethod(rawMethods));

                logic.setDependsOn(getStringList(logicMap, "dependsOn"));
                res.add(logic);
            }
        }
        return res;
    }

    private KafkaSpec mapKafka(Map<String, Object> rawKafka) {

        if (rawKafka == null) {
            throw new IllegalArgumentException(
                    "Kafka configuration cannot be null"
            );
        }

        KafkaSpec kafka = new KafkaSpec();

        String bootstrapServersEnv =
                getString(rawKafka, "bootstrapServersEnv", null);

        if (bootstrapServersEnv == null ||
                bootstrapServersEnv.isBlank()) {

            throw new IllegalArgumentException(
                    "Kafka configuration requires 'bootstrapServersEnv'. " +
                            "Example: KAFKA_BOOTSTRAP_SERVERS"
            );
        }

        kafka.setBootstrapServersEnv(bootstrapServersEnv);

        List<Map<String, Object>> rawTopics =
                getMapList(rawKafka, "topics");

        kafka.setTopics(mapKafkaTopic(rawTopics));

        List<Map<String, Object>> rawConsumers =
                getMapList(rawKafka, "consumers");

        kafka.setConsumers(mapKafkaConsumer(rawConsumers));

        return kafka;
    }

    private UrlShortenerSpec mapUrlShortener(Map<String, Object> rawUrlShortener) {
        UrlShortenerSpec urlShortener = new UrlShortenerSpec();
        urlShortener.setBaseUrl(getString(rawUrlShortener, "baseUrl", null));
        urlShortener.setExpiryDays(getInt(rawUrlShortener, "expiryDays", 0));
        urlShortener.setAnalytics(getBoolean(rawUrlShortener, "analytics", false));
        // duplicate line kept as‑is (per original logic)
        urlShortener.setExpiryDays(getInt(rawUrlShortener, "codeLength", 0));
        return urlShortener;
    }

    private ApiGatewaySpec mapApiGateway(Map<String, Object> rawApiGateway) {
        ApiGatewaySpec apiGateway = new ApiGatewaySpec();
        List<Map<String, Object>> rawRoutes = getMapList(rawApiGateway, "routes");
        apiGateway.setRoutes(mapGatewayRoute(rawRoutes));
        return apiGateway;
    }

    private List<KafkaTopicSpec> mapKafkaTopic(List<Map<String, Object>> rawKafkaTopic) {
        List<KafkaTopicSpec> res = new ArrayList<>();
        if (rawKafkaTopic != null) {
            for (Map<String, Object> topicMap : rawKafkaTopic) {
                KafkaTopicSpec topic = new KafkaTopicSpec();
                topic.setName(getString(topicMap, "name", null));
                topic.setPartitions(getInt(topicMap, "partitions", 0));
                // schema is a Map<String,String> – we can get as Map and cast
                Map<String, Object> rawSchema = getMap(topicMap, "schema");
                // Assuming schema values are Strings; we can convert or keep as Map<String,Object>
                // To preserve logic, we cast to Map<String,String> if needed, but the model expects Map<String,String>
                // We'll keep as is – may need to handle, but we don't change logic.
                @SuppressWarnings("unchecked")
                Map<String, String> schema = (Map<String, String>) (Map<?, ?>) rawSchema;
                topic.setSchema(schema);
                res.add(topic);
            }
        }
        return res;
    }

    private List<KafkaConsumerSpec> mapKafkaConsumer(List<Map<String, Object>> rawKafkaConsumer) {
        List<KafkaConsumerSpec> res = new ArrayList<>();
        if (rawKafkaConsumer != null) {
            for (Map<String, Object> consumerMap : rawKafkaConsumer) {
                KafkaConsumerSpec consumer = new KafkaConsumerSpec();
                consumer.setHandler(getString(consumerMap, "handler", null));
                consumer.setIntent(getString(consumerMap, "intent", null));
                consumer.setTopic(getString(consumerMap, "topic", null));
                consumer.setGroupId(getString(consumerMap, "groupId", null));
                res.add(consumer);
            }
        }
        return res;
    }

    private List<GatewayRouteSpec> mapGatewayRoute(List<Map<String, Object>> rawGatewayRoute) {
        List<GatewayRouteSpec> res = new ArrayList<>();
        if (rawGatewayRoute != null) {
            for (Map<String, Object> routeMap : rawGatewayRoute) {
                GatewayRouteSpec route = new GatewayRouteSpec();
                route.setUpstreamUrl(getString(routeMap, "upstreamUrl", null));
                route.setServiceName(getString(routeMap, "serviceName", null));
                route.setPathPrefix(getString(routeMap, "pathPrefix", null));
                res.add(route);
            }
        }
        return res;
    }

    private List<ParamSpec> mapParam(List<Map<String, Object>> rawParams) {
        List<ParamSpec> res = new ArrayList<>();
        if (rawParams != null) {
            for (Map<String, Object> paramMap : rawParams) {
                ParamSpec param = new ParamSpec();
                param.setName(getString(paramMap, "name", null));
                param.setType(getString(paramMap, "type", null));
                res.add(param);
            }
        }
        return res;
    }

    private List<MethodSpec> mapMethod(List<Map<String, Object>> rawMethod) {
        List<MethodSpec> res = new ArrayList<>();
        if (rawMethod != null) {
            for (Map<String, Object> methodMap : rawMethod) {
                MethodSpec method = new MethodSpec();
                method.setName(getString(methodMap, "name", null));

                List<Map<String, Object>> rawParams = getMapList(methodMap, "params");
                method.setParams(mapParam(rawParams));

                method.setReturns(getString(methodMap, "returns", null));
                method.setIntent(getString(methodMap, "intent", null));
                res.add(method);
            }
        }
        return res;
    }

    private List<EndpointSpec> mapEndpoint(List<Map<String, Object>> rawEndpoint) {
        List<EndpointSpec> res = new ArrayList<>();
        if (rawEndpoint != null) {
            for (Map<String, Object> endpointMap : rawEndpoint) {
                EndpointSpec endpoint = new EndpointSpec();
                endpoint.setHandler(getString(endpointMap, "handler", null));
                endpoint.setPath(getString(endpointMap, "path", null));
                endpoint.setMethod(getString(endpointMap, "method", null));
                endpoint.setRequestBody(getString(endpointMap, "requestBody", null));
                endpoint.setResponseBody(getString(endpointMap, "responseBody", null));
                res.add(endpoint);
            }
        }
        return res;
    }
}