package com.notfound.aiservice.agent.tool.impl;

import java.util.List;
import java.util.Map;

/** Helper bóc tách tham số từ Map mà Gemini trả về (kiểu lỏng lẻo: Number, String, List...). */
final class ArgUtil {
    private ArgUtil() {
    }

    static String getString(Map<String, Object> args, String key, String defaultValue) {
        if (args == null) return defaultValue;
        Object v = args.get(key);
        return v == null ? defaultValue : String.valueOf(v);
    }

    static int getInt(Map<String, Object> args, String key, int defaultValue) {
        if (args == null) return defaultValue;
        Object v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static Double getDouble(Map<String, Object> args, String key, Double defaultValue) {
        if (args == null) return defaultValue;
        Object v = args.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    static List<String> getStringList(Map<String, Object> args, String key) {
        if (args == null) return List.of();
        Object v = args.get(key);
        if (v instanceof List<?> raw) {
            return raw.stream().map(String::valueOf).toList();
        }
        if (v instanceof String s && !s.isBlank()) {
            return List.of(s.split("\\s*,\\s*"));
        }
        return List.of();
    }
}
