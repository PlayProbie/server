package com.playprobie.api.global.converter;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Map<String, List<String>> ↔ JSON 변환 컨버터
 * themDetails 필드용
 */
@Converter
public class ThemeDetailsMapConverter implements AttributeConverter<Map<String, List<String>>, String> {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(Map<String, List<String>> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(attribute);
		} catch (Exception e) {
			throw new RuntimeException("Failed to convert map to JSON", e);
		}
	}

	@Override
	public Map<String, List<String>> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isEmpty()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(dbData, new TypeReference<Map<String, List<String>>>() {});
		} catch (Exception e) {
			throw new RuntimeException("Failed to convert JSON to map", e);
		}
	}
}
