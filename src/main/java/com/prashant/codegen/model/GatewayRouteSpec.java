package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GatewayRouteSpec {
    private String serviceName;
    private String pathPrefix;
    private String upstreamUrl;

}
