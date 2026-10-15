package com.evision.ragservice.domain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ToolSchema {

    public static final int MAX_NAME_CHARS = 64;
    public static final int MAX_DESCRIPTION_CHARS = 300;
    public static final int MAX_PROPERTIES = 8;
    public static final int MAX_ENUM_VALUES = 20;

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1," + MAX_NAME_CHARS + "}$");
    private static final Set<String> TOP_KEYS = Set.of("type", "properties", "required", "additionalProperties");
    private static final Set<String> PROPERTY_KEYS = Set.of("type", "description", "enum", "maxLength");
    private static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean");
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "<|", "|>", "<tool_call>", "</tool_call>", "<tool_response>", "</tool_response>");

    private ToolSchema() {
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static void requireValidDefinitions(List<ToolDefinition> definitions, int maxTools, int maxTotalChars) {
        if (definitions.size() > maxTools) {
            throw new InvalidContentException("at most " + maxTools + " tools");
        }
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (ToolDefinition definition : definitions) {
            String name = definition.name();
            if (!isValidName(name)) {
                throw new InvalidContentException(
                        "tool names must match [A-Za-z0-9_] and be 1 to " + MAX_NAME_CHARS + " characters");
            }
            if (!seen.add(name)) {
                throw new InvalidContentException("tool '" + name + "': duplicate tool name");
            }
            requireValidDescription("tool '" + name + "': description", definition.description());
            requireValidParameters(name, definition.parameters());
            total += jsonChars(Map.of("name", name, "description",
                    definition.description() == null ? "" : definition.description(),
                    "parameters", definition.parameters() == null ? Map.of() : definition.parameters()));
        }
        if (total > maxTotalChars) {
            throw new InvalidContentException("tool definitions exceed " + maxTotalChars + " characters");
        }
    }

    public static List<String> violations(Map<String, Object> schema, Map<String, Object> arguments) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> properties = propertiesOf(schema);
        for (Object required : requiredOf(schema)) {
            if (arguments == null || !arguments.containsKey(required)) {
                problems.add("missing required argument '" + required + "'");
            }
        }
        if (arguments == null) {
            return problems;
        }
        arguments.forEach((key, value) -> {
            if (!(properties.get(key) instanceof Map<?, ?> property)) {
                problems.add(isValidName(key) ? "unknown argument '" + key + "'" : "unknown argument");
                return;
            }
            checkValue(key, property, value, problems);
        });
        return problems;
    }

    public static int jsonChars(Object value) {
        if (value == null) {
            return 4;
        }
        if (value instanceof Map<?, ?> map) {
            int chars = 2 + Math.max(0, map.size() - 1);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                chars += String.valueOf(entry.getKey()).length() + 3 + jsonChars(entry.getValue());
            }
            return chars;
        }
        if (value instanceof List<?> list) {
            int chars = 2 + Math.max(0, list.size() - 1);
            for (Object element : list) {
                chars += jsonChars(element);
            }
            return chars;
        }
        if (value instanceof String s) {
            return s.length() + 2;
        }
        if (value instanceof Boolean b) {
            return b ? 4 : 5;
        }
        return value.toString().length();
    }

    private static void checkValue(String key, Map<?, ?> property, Object value, List<String> problems) {
        String type = String.valueOf(property.get("type"));
        boolean typeOk = switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long || value instanceof BigInteger;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            default -> false;
        };
        if (!typeOk) {
            problems.add("argument '" + key + "' must be " + type);
            return;
        }
        if (property.get("enum") instanceof List<?> allowed && !allowed.contains(value)) {
            problems.add("argument '" + key + "' not in enum");
        }
        if (value instanceof String s && property.get("maxLength") instanceof Integer max && s.length() > max) {
            problems.add("argument '" + key + "' exceeds maxLength " + max);
        }
    }

    private static void requireValidParameters(String tool, Map<String, Object> parameters) {
        String owner = "tool '" + tool + "': ";
        if (parameters == null || !"object".equals(parameters.get("type"))) {
            throw new InvalidContentException(owner + "parameters type must be object");
        }
        for (String key : parameters.keySet()) {
            if (!TOP_KEYS.contains(key)) {
                throw new InvalidContentException(owner + "parameters keyword '" + safeKey(key) + "' is not supported");
            }
        }
        if (parameters.containsKey("additionalProperties") && !Boolean.FALSE.equals(parameters.get("additionalProperties"))) {
            throw new InvalidContentException(owner + "additionalProperties must be false");
        }
        Map<String, Object> properties = propertiesOf(parameters);
        if (parameters.containsKey("properties") && !(parameters.get("properties") instanceof Map)) {
            throw new InvalidContentException(owner + "properties must be an object");
        }
        if (properties.size() > MAX_PROPERTIES) {
            throw new InvalidContentException(owner + "at most " + MAX_PROPERTIES + " properties");
        }
        properties.forEach((name, property) -> requireValidProperty(owner, name, property));
        if (parameters.containsKey("required")) {
            if (!(parameters.get("required") instanceof List<?> required)) {
                throw new InvalidContentException(owner + "required must be a list");
            }
            for (Object name : required) {
                if (!properties.containsKey(name)) {
                    throw new InvalidContentException(owner + "required names an undefined property");
                }
            }
        }
    }

    private static void requireValidProperty(String owner, String name, Object property) {
        String field = owner + "property '" + safeKey(name) + "' ";
        if (!isValidName(name)) {
            throw new InvalidContentException(owner + "property names must match [A-Za-z0-9_]");
        }
        if (!(property instanceof Map<?, ?> map)) {
            throw new InvalidContentException(field + "must be an object");
        }
        for (Object key : map.keySet()) {
            if (!PROPERTY_KEYS.contains(key)) {
                throw new InvalidContentException(field + "keyword '" + safeKey(String.valueOf(key)) + "' is not supported");
            }
        }
        Object type = map.get("type");
        if (!(type instanceof String t) || !TYPES.contains(t)) {
            throw new InvalidContentException(field + "type must be string, integer, number or boolean");
        }
        if (map.get("description") instanceof String description) {
            requireValidDescription(field + "description", description);
        } else if (map.containsKey("description")) {
            throw new InvalidContentException(field + "description must be a string");
        }
        if (map.containsKey("enum")) {
            if (!(map.get("enum") instanceof List<?> values) || values.isEmpty() || values.size() > MAX_ENUM_VALUES) {
                throw new InvalidContentException(field + "enum must list 1 to " + MAX_ENUM_VALUES + " values");
            }
            for (Object value : values) {
                if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                    throw new InvalidContentException(field + "enum values must be scalars");
                }
            }
        }
        if (map.containsKey("maxLength")
                && (!"string".equals(t) || !(map.get("maxLength") instanceof Integer max) || max < 1)) {
            throw new InvalidContentException(field + "maxLength must be a positive integer on a string property");
        }
    }

    private static void requireValidDescription(String field, String description) {
        if (description == null) {
            return;
        }
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            throw new InvalidContentException(field + " must not exceed " + MAX_DESCRIPTION_CHARS + " characters");
        }
        MetadataRules.rejectNul(field, description);
        for (String token : FORBIDDEN_TOKENS) {
            if (description.contains(token)) {
                throw new InvalidContentException(field + " must not contain template control tokens");
            }
        }
    }

    private static String safeKey(String key) {
        return isValidName(key) ? key : "?";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Map<String, Object> schema) {
        return schema != null && schema.get("properties") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<?> requiredOf(Map<String, Object> schema) {
        return schema != null && schema.get("required") instanceof List<?> l ? l : List.of();
    }
}
