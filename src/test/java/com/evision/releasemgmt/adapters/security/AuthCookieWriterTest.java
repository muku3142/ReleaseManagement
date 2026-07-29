package com.evision.releasemgmt.adapters.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthCookieWriterTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_TTL = Duration.ofMinutes(30);

    @Test
    void writeEmitsBothCookiesWithExpectedFlagsAndPaths() {
        MockEnvironment environment = new MockEnvironment();
        AuthCookieWriter writer = new AuthCookieWriter(true, ACCESS_TTL, REFRESH_TTL, environment);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenPair pair = new TokenPair("access-value", "refresh-value", Instant.now().plus(Duration.ofHours(8)));

        writer.write(response, pair);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        String accessCookie = cookies.stream().filter(c -> c.startsWith(AuthCookieWriter.ACCESS_COOKIE + "=")).findFirst().orElseThrow();
        String refreshCookie = cookies.stream().filter(c -> c.startsWith(AuthCookieWriter.REFRESH_COOKIE + "=")).findFirst().orElseThrow();

        assertThat(accessCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/");
        assertThat(refreshCookie).contains("Path=/api/auth");
    }

    @Test
    void insecureCookieAllowedUnderLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        AuthCookieWriter writer = new AuthCookieWriter(false, ACCESS_TTL, REFRESH_TTL, environment);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenPair pair = new TokenPair("access-value", "refresh-value", Instant.now().plus(Duration.ofHours(8)));

        writer.write(response, pair);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).doesNotContain("Secure"));
    }

    @Test
    void insecureCookieRejectedOutsideLocalProfile() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> new AuthCookieWriter(false, ACCESS_TTL, REFRESH_TTL, environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clearEmitsBothCookiesWithZeroMaxAgeAndSamePaths() {
        MockEnvironment environment = new MockEnvironment();
        AuthCookieWriter writer = new AuthCookieWriter(true, ACCESS_TTL, REFRESH_TTL, environment);
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.clear(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
        String refreshCookie = cookies.stream().filter(c -> c.startsWith(AuthCookieWriter.REFRESH_COOKIE + "=")).findFirst().orElseThrow();
        assertThat(refreshCookie).contains("Path=/api/auth");
    }
}
