package com.evision.releasemgmt.adapters.security;

import java.time.Instant;

public record TokenPair(String accessToken, String refreshToken, Instant absoluteExpiry) {
}
