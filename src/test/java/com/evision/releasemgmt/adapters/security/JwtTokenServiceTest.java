package com.evision.releasemgmt.adapters.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String FIXTURE_SECRET_B64 = "and0LXRlc3Qtc2VjcmV0LTAxMjM0NTY3ODlhYmNkZWY=";
    private static final SecretKey KEY = new SecretKeySpec(
            Base64.getDecoder().decode(FIXTURE_SECRET_B64), "HmacSHA256");

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    private final JwtDecoder accessDecoder = decoder(KEY, JwtTokenService.TYPE_ACCESS);
    private final JwtDecoder refreshDecoder = decoder(KEY, JwtTokenService.TYPE_REFRESH);

    private final JwtTokenService service = new JwtTokenService(
            encoder, refreshDecoder, Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(8));

    private static JwtDecoder decoder(SecretKey key, String expectedType) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new TokenTypeValidator(expectedType),
                new AbsoluteExpiryValidator()));
        return decoder;
    }

    @Test
    void issueReturnsDistinctTokensWithExpectedAccessClaims() {
        TokenPair pair = service.issue("557058:abc-123", "rm@acme.com");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.accessToken()).isNotEqualTo(pair.refreshToken());

        Jwt decoded = accessDecoder.decode(pair.accessToken());
        assertThat(decoded.getSubject()).isEqualTo("557058:abc-123");
        assertThat(decoded.getClaimAsString(JwtTokenService.CLAIM_EMAIL)).isEqualTo("rm@acme.com");
        assertThat(decoded.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE)).isEqualTo(JwtTokenService.TYPE_ACCESS);
        assertThat(decoded.<Long>getClaim(JwtTokenService.CLAIM_ABSOLUTE_EXP)).isNotNull();
    }

    @Test
    void refreshTokenDecodesWithRefreshDecoderAndCarriesRefreshType() {
        TokenPair pair = service.issue("557058:abc-123", "rm@acme.com");

        Jwt decoded = refreshDecoder.decode(pair.refreshToken());
        assertThat(decoded.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE)).isEqualTo(JwtTokenService.TYPE_REFRESH);
    }

    @Test
    void crossTypeReplayIsRejectedByBothDecoders() {
        TokenPair pair = service.issue("557058:abc-123", "rm@acme.com");

        assertThatThrownBy(() -> accessDecoder.decode(pair.refreshToken()))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> refreshDecoder.decode(pair.accessToken()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void capIsPreservedAcrossASingleRefresh() {
        // absolute_exp is carried forward unchanged by refresh(), never recomputed
        TokenPair original = service.issue("557058:abc-123", "rm@acme.com");

        TokenPair refreshed = service.refresh(original.refreshToken());

        assertThat(refreshed.absoluteExpiry()).isEqualTo(original.absoluteExpiry());
        Jwt originalRefreshJwt = refreshDecoder.decode(original.refreshToken());
        Jwt newAccessJwt = accessDecoder.decode(refreshed.accessToken());
        assertThat(newAccessJwt.getExpiresAt()).isAfterOrEqualTo(originalRefreshJwt.getIssuedAt());
    }

    @Test
    void capIsNotExtendableAcrossChainedRefreshes() {
        // three consecutive refreshes must not move the absolute_exp claim forward
        TokenPair pair = service.issue("557058:abc-123", "rm@acme.com");
        Instant originalCap = pair.absoluteExpiry();

        TokenPair r1 = service.refresh(pair.refreshToken());
        TokenPair r2 = service.refresh(r1.refreshToken());
        TokenPair r3 = service.refresh(r2.refreshToken());

        assertThat(r1.absoluteExpiry()).isEqualTo(originalCap);
        assertThat(r2.absoluteExpiry()).isEqualTo(originalCap);
        assertThat(r3.absoluteExpiry()).isEqualTo(originalCap);
    }

    @Test
    void refreshPastAbsoluteCapThrowsSessionExpired() {
        Instant absoluteExpiry = Instant.now().minusSeconds(1);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("557058:abc-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .id(UUID.randomUUID().toString())
                .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TYPE_REFRESH)
                .claim(JwtTokenService.CLAIM_EMAIL, "rm@acme.com")
                .claim(JwtTokenService.CLAIM_ABSOLUTE_EXP, absoluteExpiry.getEpochSecond())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredCapRefreshToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> service.refresh(expiredCapRefreshToken))
                .isInstanceOf(SessionRefreshException.class)
                .extracting(ex -> ((SessionRefreshException) ex).reason())
                .isEqualTo(SessionRefreshException.Reason.SESSION_EXPIRED);
    }

    @Test
    void algNoneTokenIsRejected() {
        String header = base64Url("""
                {"alg":"none","typ":"JWT"}""");
        String payload = base64Url("""
                {"sub":"557058:abc-123","token_type":"access","email":"rm@acme.com",\
                "absolute_exp":9999999999,"exp":9999999999,"iat":1000000000,"jti":"x"}""");
        String forgedToken = header + "." + payload + ".";

        assertThatThrownBy(() -> accessDecoder.decode(forgedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        SecretKey otherKey = new SecretKeySpec(
                "different-32-byte-secret-key-0000".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("557058:abc-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TYPE_ACCESS)
                .claim(JwtTokenService.CLAIM_EMAIL, "rm@acme.com")
                .claim(JwtTokenService.CLAIM_ABSOLUTE_EXP, Instant.now().plusSeconds(28800).getEpochSecond())
                .build();
        JwsHeader otherHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String wrongSecretToken = otherEncoder.encode(JwtEncoderParameters.from(otherHeader, claims)).getTokenValue();

        assertThatThrownBy(() -> accessDecoder.decode(wrongSecretToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedAccessTokenIsRejected() {
        TokenPair pair = service.issue("557058:abc-123", "rm@acme.com");
        String[] segments = pair.accessToken().split("\\.");
        char[] payloadChars = segments[1].toCharArray();
        int flipIndex = payloadChars.length / 2;
        payloadChars[flipIndex] = payloadChars[flipIndex] == 'A' ? 'B' : 'A';
        segments[1] = new String(payloadChars);
        String tamperedToken = String.join(".", segments);

        assertThatThrownBy(() -> accessDecoder.decode(tamperedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageStringAtRefreshThrowsInvalidSession() {
        assertThatThrownBy(() -> service.refresh("not-a-jwt-at-all"))
                .isInstanceOf(SessionRefreshException.class)
                .extracting(ex -> ((SessionRefreshException) ex).reason())
                .isEqualTo(SessionRefreshException.Reason.INVALID_SESSION);
    }

    @Test
    void secretShorterThan32BytesFailsFastWithClearMessage() {
        com.evision.releasemgmt.config.JwtConfig jwtConfig = new com.evision.releasemgmt.config.JwtConfig();
        String sixteenByteSecretB64 = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> jwtConfig.jwtSigningKey(sixteenByteSecretB64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
