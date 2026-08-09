package com.evision.releasemgmt.application;

import java.util.List;

public record RenderResult(String html, List<String> unknownTokens) {
}
