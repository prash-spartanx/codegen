package com.prashant.codegen.architect;

import java.io.IOException;
import java.util.Map;

public interface LlmArchitect {
    public Map<String,Object> generateYaml(String modelName ,String description) throws IOException, InterruptedException;
}
