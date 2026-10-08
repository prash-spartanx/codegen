package com.prashant.codegen.util;

public final class TextCleaningUtils {
    private TextCleaningUtils() {}

    public static String stripCodeFences(String text) {

        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        if (result.startsWith("```")) {
            int firstNewline = result.indexOf('\n');

            if (firstNewline != -1) {
                result = result.substring(firstNewline + 1);
            }
        }

        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }

        return result;
    }
}