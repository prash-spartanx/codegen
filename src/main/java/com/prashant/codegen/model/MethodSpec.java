package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MethodSpec {
    private String name;
    private List<ParamSpec> params;
    private String returns;
    private String intent;
}
