package com.evision.ragservice.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class MetadataRules {

    public static final int MAX_KEYS = 50;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_STRING_VALUE_LENGTH = 1000;
    public static final int MAX_IN_LIST_VALUES = 100;

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.-]{1," + MAX_KEY_LENGTH + "}$");

    private MetadataRules() {
    }

    public static void validateMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        requireKeyCount("metadata", metadata);
        metadata.forEach((key, value) -> {
            requireValidKey("metadata", key);
            requireScalar("metadata value for key '" + key + "'", value);
        });
    }

    public static MetadataFilter parseFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return MetadataFilter.none();
        }
        requireKeyCount("filter", filter);
        List<MetadataFilter.Clause> clauses = new ArrayList<>();
        filter.forEach((key, value) -> {
            requireValidKey("filter", key);
            clauses.add(new MetadataFilter.Clause(key, anyOf(key, value)));
        });
        return new MetadataFilter(List.copyOf(clauses));
    }

    public static void rejectNul(String field, String value) {
        if (value != null && value.indexOf('\u0000') >= 0) {
            throw new InvalidContentException(field + " must not contain NUL characters");
        }
    }

    private static List<Object> anyOf(String key, Object value) {
        String field = "filter value for key '" + key + "'";
        if (value instanceof List<?> list) {
            if (list.isEmpty() || list.size() > MAX_IN_LIST_VALUES) {
                throw new InvalidContentException(
                        field + " must list between 1 and " + MAX_IN_LIST_VALUES + " values");
            }
            List<Object> values = new ArrayList<>(list.size());
            for (Object element : list) {
                requireScalar(field, element);
                values.add(element);
            }
            return List.copyOf(values);
        }
        requireScalar(field, value);
        return List.of(value);
    }

    private static void requireKeyCount(String owner, Map<String, Object> map) {
        if (map.size() > MAX_KEYS) {
            throw new InvalidContentException(owner + " must not have more than " + MAX_KEYS + " keys");
        }
    }

    private static void requireValidKey(String owner, String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new InvalidContentException(
                    owner + " keys must match [A-Za-z0-9_.-] and be 1 to " + MAX_KEY_LENGTH + " characters");
        }
    }

    private static void requireScalar(String field, Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw new InvalidContentException(field + " must be a string or a number");
        }
        if (value instanceof String s) {
            if (s.length() > MAX_STRING_VALUE_LENGTH) {
                throw new InvalidContentException(field + " must not exceed " + MAX_STRING_VALUE_LENGTH + " characters");
            }
            rejectNul(field, s);
        }
    }
}
