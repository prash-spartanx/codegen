package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceLogicSpec {
    private String name;
    private List<String> dependsOn;
    private List<MethodSpec> methods;
}
