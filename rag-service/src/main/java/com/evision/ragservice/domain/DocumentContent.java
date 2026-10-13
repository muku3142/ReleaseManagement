package com.evision.ragservice.domain;

import java.util.Map;

public record DocumentContent(String externalId, String title, String text, String url, Map<String, Object> metadata) {
}
