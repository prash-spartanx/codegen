package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JwtAuthSpec {
    private String secretEnv;
    private String algorithm;
    private int accessTokenExpirationMinutes;
    private int refreshTokenExpirationDays;

}
