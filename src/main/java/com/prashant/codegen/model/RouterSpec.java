package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouterSpec {
    private String name;
    private String prefix;
    private boolean authRequired;
    private List<String> dependsOn;
    private List<EndpointSpec> endpoints;
}
