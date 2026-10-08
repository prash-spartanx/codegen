package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EndpointSpec {
    private String method;
    private String path;
    private String handler;
    private String requestBody;
    private String responseBody;
}
