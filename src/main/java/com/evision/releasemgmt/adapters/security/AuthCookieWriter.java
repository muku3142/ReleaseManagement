package com.evision.releasemgmt.adapters.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieWriter {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private final boolean secure;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthCookieWriter(@Value("${app.security.cookie-secure}") boolean secure,
                             @Value("${app.jwt.access-ttl}") Duration accessTtl,
                             @Value("${app.jwt.refresh-ttl}") Duration refreshTtl,
                             Environment environment) {
        if (!secure && !environment.matchesProfiles("local")) {
            throw new IllegalStateException(
                    "app.security.cookie-secure=false is only permitted under the 'local' profile");
        }
        this.secure = secure;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public void write(HttpServletResponse response, TokenPair pair) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(pair.accessToken(), accessTtl).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(pair.refreshToken(), refreshTtl).toString());
    }

    public void clear(HttpServletResponse response) {
        // same paths as write(): a mismatched path silently fails to clear the cookie
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie("", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(ACCESS_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
