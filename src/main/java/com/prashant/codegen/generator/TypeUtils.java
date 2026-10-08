package com.prashant.codegen.generator;
public class TypeUtils {
    public static String stripWrapper(String type) {
        if (type == null) return null;
        if (type.startsWith("List[") && type.endsWith("]")) {
            return type.substring(5, type.length() - 1);
        }
        if (type.startsWith("Optional[") && type.endsWith("]")) {
            return type.substring(9, type.length() - 1);
        }
        return type;
    }
}
