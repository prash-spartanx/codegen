package com.prashant.codegen.mapper;

import java.util.List;
import java.util.Map;

/**
 * Utility methods for safe, null‑aware extraction of typed values from raw YAML maps.
 */
public final class MapUtils {

    private MapUtils() {
        // prevent instantiation
    }

    // ---- Strings ----

    /**
     * Returns the value as a String representation of a scalar.
     * Converts strings, booleans, and numbers using String.valueOf().
     * Throws an exception if the key is missing or if the value is a complex collection (List/Map).
     */
    public static String getString(Map<String, Object> raw, String key) {
        Object val = raw.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }
        if (val instanceof Map || val instanceof List) {
            throw new IllegalArgumentException("Key '" + key + "' is not a scalar: " + val);
        }
        return String.valueOf(val);
    }

    /**
     * Returns the value as a String representation of a scalar,
     * or defaultValue if the key is missing.
     * Converts strings, booleans, and numbers using String.valueOf().
     */
    public static String getString(Map<String, Object> raw, String key, String defaultValue) {
        Object val = raw.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Map || val instanceof List) {
            throw new IllegalArgumentException("Key '" + key + "' is not a scalar: " + val);
        }
        return String.valueOf(val);
    }

    // ---- Integers ----

    /**
     * Returns the value as an int, or defaultValue if the key is missing.
     */
    public static int getInt(Map<String, Object> raw, String key, int defaultValue) {
        Object val = raw.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (!(val instanceof Number)) {
            throw new IllegalArgumentException("Key '" + key + "' is not a number: " + val);
        }
        return ((Number) val).intValue();
    }

    // ---- Booleans ----

    /**
     * Returns the value as a boolean, or defaultValue if the key is missing.
     */
    public static boolean getBoolean(Map<String, Object> raw, String key, boolean defaultValue) {
        Object val = raw.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (!(val instanceof Boolean)) {
            throw new IllegalArgumentException("Key '" + key + "' is not a boolean: " + val);
        }
        return (Boolean) val;
    }

    // ---- Lists of Maps ----

    /**
     * Returns the value as a List<Map<String,Object>>, or null if the key is missing.
     * (Assumes the caller will handle null gracefully.)
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getMapList(Map<String, Object> raw, String key) {
        Object val = raw.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof List)) {
            throw new IllegalArgumentException("Key '" + key + "' is not a List: " + val);
        }
        // Check that each element is a Map
        List<?> list = (List<?>) val;
        for (Object item : list) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Key '" + key + "' contains non-Map element: " + item);
            }
        }
        return (List<Map<String, Object>>) list;
    }

    // ---- Lists of Strings ----

    /**
     * Returns the value as a List<String>, or null if the key is missing.
     */
    @SuppressWarnings("unchecked")
    public static List<String> getStringList(Map<String, Object> raw, String key) {
        Object val = raw.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof List)) {
            throw new IllegalArgumentException("Key '" + key + "' is not a List: " + val);
        }
        List<?> list = (List<?>) val;
        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException("Key '" + key + "' contains non-String element: " + item);
            }
        }
        return (List<String>) list;
    }

    // ---- Single Map ----

    /**
     * Returns the value as a Map<String,Object>, or null if the key is missing.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> raw, String key) {
        Object val = raw.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof Map)) {
            throw new IllegalArgumentException("Key '" + key + "' is not a Map: " + val);
        }
        return (Map<String, Object>) val;
    }
}