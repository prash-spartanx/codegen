package com.prashant.codegen;

import com.prashant.codegen.cli.GenerateCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

@SpringBootApplication
public class CodegenApplication implements CommandLineRunner {

    private final GenerateCommand generateCommand;

    @Autowired
    public CodegenApplication(GenerateCommand generateCommand) {
        this.generateCommand = generateCommand;
    }

    public static void main(String[] args) {

        SpringApplication.run(
                CodegenApplication.class,
                args
        );
    }

    @Override
    public void run(String... args) {

        int exitCode =
                new CommandLine(generateCommand)
                        .execute(args);

        System.exit(exitCode);
    }
}