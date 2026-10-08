package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
@ToString

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectSpec {
    private String name;
    private String description;
    private String pythonVersion;
    private List<String> services;
    private List<EntitySpec> entities;
    private List<RouterSpec> routers;
    private List<ServiceLogicSpec> serviceLogics;
    private JwtAuthSpec jwtAuth;
    private RateLimiterSpec rateLimiter;
    private KafkaSpec kafka;
    private UrlShortenerSpec urlShortener;
    private ApiGatewaySpec apiGateway;

}
