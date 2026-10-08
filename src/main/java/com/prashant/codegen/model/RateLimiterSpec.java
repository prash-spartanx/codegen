package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateLimiterSpec {
    private String backend;
    private int defaultLimit;
    private int windowSeconds;
    private boolean perUser;
    private String redisUrlEnv; // only meaningful when backend == "redis"; null/unused otherwise
}
