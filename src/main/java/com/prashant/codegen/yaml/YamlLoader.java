package com.prashant.codegen.yaml;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class YamlLoader {

    public Map<String, Object> loadFromFile(String yamlFilePath) {
        try (InputStream inputStream = new FileInputStream(yamlFilePath)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(inputStream);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            } else {
                throw new IllegalArgumentException("YAML root is not a map: " + yamlFilePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load YAML file: " + yamlFilePath, e);
        }
    }

    public Map<String, Object> loadFromString(String yamlContent) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(yamlContent);
        if (loaded instanceof Map) {
            return (Map<String, Object>) loaded;
        } else {
            throw new IllegalArgumentException("YAML root is not a map from string content");
        }
    }
}
