package com.prashant.codegen.orchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileWriter {
    public void WriteFile(String outputRoot , String relativePath , String content){
        Path path = Paths.get(outputRoot, relativePath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
