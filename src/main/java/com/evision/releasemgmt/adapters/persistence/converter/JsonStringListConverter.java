package com.evision.releasemgmt.adapters.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public JsonStringListConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(List<String> value) {
        return objectMapper.writeValueAsString(value == null ? List.of() : value);
    }

    @Override
    public List<String> convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(stored, LIST_OF_STRING);
    }
}
