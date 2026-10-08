package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntitySpec {
    private String name;
    private List<FieldSpec> fields;
    private List<RelationshipSpec> relationships;
}
