package com.prashant.codegen.llm;

import com.prashant.codegen.util.TextCleaningUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cleans raw LLM text output before it gets spliced into a generated Python file:
 *
 * 1. Strips markdown code fences if the model added them.
 * 2. Normalizes tabs.
 * 3. Wraps the method body inside a temporary Python function so that
 *    Black can understand the actual Python block structure.
 * 4. Runs Black to format the Python code correctly.
 * 5. Extracts the formatted function body.
 * 6. Adds the required class-method indentation.
 */
@Component
public class LlmOutputCleaner {

    private static final int PYTHON_INDENT_UNIT = 4;

    public String clean(String rawResponse, int targetIndentSpaces)
            throws IOException, InterruptedException {

        String text = TextCleaningUtils.stripCodeFences(rawResponse);
//        // print the text after stripping code fences
//        System.out.println("After stripping code fences:\n" + text);
        text = normalizeTabs(text);
//        // print the text after normalizing tabs
//        System.out.println("After normalizing tabs:\n" + text);
        text = normalizeBodyIndentation(text);
//        // print the text after normalizing body indentation
//        System.out.println("After normalizing body indentation:\n" + text);

        return formatWithBlack(text, targetIndentSpaces);
    }

    /**
     * Defensive normalization:
     * converts tabs into normal Python indentation spaces.
     */
    private String normalizeTabs(String text) {
        return text.replace("\t", " ".repeat(PYTHON_INDENT_UNIT));
    }

    /**
     * Removes the method-level indentation from the LLM output.
     *
     * The LLM is instructed to return the body already indented by 8 spaces.
     * Black will instead receive a temporary complete function, so the body
     * must first be converted back to relative indentation.
     */
    private String normalizeBodyIndentation(String text) {

        List<String> lines = new ArrayList<>(
                List.of(text.split("\n", -1))
        );

        while (!lines.isEmpty() && lines.get(0).isBlank()) {
            lines.remove(0);
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }

        if (lines.isEmpty()) {
            return "";
        }

        int minimumIndent = Integer.MAX_VALUE;

        for (String line : lines) {

            if (line.isBlank()) {
                continue;
            }

            int indent = leadingSpaces(line);
            minimumIndent = Math.min(minimumIndent, indent);
        }

        if (minimumIndent == Integer.MAX_VALUE) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {

            String line = lines.get(i);

            if (!line.isBlank()) {
                int removeCount =
                        Math.min(minimumIndent, leadingSpaces(line));

                line = line.substring(removeCount);
            }

            result.append(line);

            if (i < lines.size() - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Wraps the generated method body inside a temporary Python function,
     * sends the complete Python source to Black through stdin, then extracts
     * the formatted function body.
     */
    private String formatWithBlack(
            String body,
            int targetIndentSpaces)
            throws IOException, InterruptedException {

        if (body == null || body.isBlank()) {
            throw new IOException(
                    "LLM returned an empty method body after cleaning");
        }

        String temporaryFunction =
                "def __generated_method__():\n"
                        + indentForTemporaryFunction(body)
                        + "\n";

        Process process = new ProcessBuilder(
                "python",
                "-m",
                "black",
                "--quiet",
                "-"
        )
                .redirectErrorStream(false)
                .start();

        try (OutputStream outputStream = process.getOutputStream()) {
            outputStream.write(
                    temporaryFunction.getBytes(StandardCharsets.UTF_8)
            );
        }

        String formattedOutput;
        String errorOutput;

        try (InputStream stdout = process.getInputStream();
             InputStream stderr = process.getErrorStream()) {

            formattedOutput =
                    new String(
                            readAll(stdout),
                            StandardCharsets.UTF_8
                    );

            errorOutput =
                    new String(
                            readAll(stderr),
                            StandardCharsets.UTF_8
                    );
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException(
                    "Black failed to format generated Python code. "
                            + "Exit code: "
                            + exitCode
                            + ". "
                            + errorOutput.strip()
            );
        }

        return extractFormattedBody(
                formattedOutput,
                targetIndentSpaces
        );
    }

    /**
     * Adds one normal Python indentation level to the temporary function body.
     */
    private String indentForTemporaryFunction(String body) {

        StringBuilder result = new StringBuilder();

        String[] lines = body.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {

            String line = lines[i];

            if (!line.isBlank()) {
                result.append("    ");
            }

            result.append(line);

            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Extracts everything inside __generated_method__() and changes its
     * indentation from function-level indentation to the actual service
     * method indentation.
     */
    private String extractFormattedBody(
            String formattedFunction,
            int targetIndentSpaces) {

        String[] lines =
                formattedFunction.split("\n", -1);

        String targetPrefix =
                " ".repeat(targetIndentSpaces);

        StringBuilder result =
                new StringBuilder();

        boolean insideFunction = false;

        for (String line : lines) {

            if (!insideFunction) {

                if (line.stripTrailing()
                        .equals("def __generated_method__():")) {

                    insideFunction = true;
                }

                continue;
            }

            if (line.isBlank()) {

                result.append("\n");
                continue;
            }

            String content = line;

            /*
             * Remove exactly one function indentation level.
             * Black formats the function body using 4 spaces.
             */
            if (content.startsWith(" ".repeat(PYTHON_INDENT_UNIT))) {
                content = content.substring(PYTHON_INDENT_UNIT);
            }

            result.append(targetPrefix)
                    .append(content);

            result.append("\n");
        }

        return result.toString().stripTrailing();
    }

    private int leadingSpaces(String line) {

        int count = 0;

        while (count < line.length()
                && line.charAt(count) == ' ') {

            count++;
        }

        return count;
    }

    private byte[] readAll(InputStream inputStream)
            throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];

        int read;

        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }

        return output.toByteArray();
    }
}