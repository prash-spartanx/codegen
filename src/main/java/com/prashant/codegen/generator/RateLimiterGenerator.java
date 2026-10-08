package com.prashant.codegen.generator;

import com.prashant.codegen.model.RateLimiterSpec;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class RateLimiterGenerator {

    public String generate(RateLimiterSpec rateLimiterSpec, Configuration configuration)
            throws IOException, TemplateException {

        // HashMap, not Map.of() — Map.of() throws NPE on any null value, and
        // redisUrlEnv is legitimately null when backend != "redis".
        Map<String, Object> data = new HashMap<>();
        data.put("Backend", rateLimiterSpec.getBackend());
        data.put("Limit", rateLimiterSpec.getDefaultLimit());
        data.put("Window", rateLimiterSpec.getWindowSeconds());
        data.put("isPerUser", rateLimiterSpec.isPerUser());
        data.put("redisUrlEnv", rateLimiterSpec.getRedisUrlEnv()); // may be null — fine for HashMap

        var template = configuration.getTemplate("rate_limiter.py.ftl");
        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }
}
