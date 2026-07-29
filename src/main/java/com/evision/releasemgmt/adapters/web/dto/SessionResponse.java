package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;

public record SessionResponse(String accountId, String email, Instant absoluteExpiry) {
}
