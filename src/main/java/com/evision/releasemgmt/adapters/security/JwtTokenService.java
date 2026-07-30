package com.evision.releasemgmt.adapters.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class JwtTokenService {

    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_ABSOLUTE_EXP = "absolute_exp";
    public static final String CLAIM_EMAIL = "email";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration absoluteTtl;

    public JwtTokenService(JwtEncoder jwtEncoder,
                            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
                            @Value("${app.jwt.access-ttl}") Duration accessTtl,
                            @Value("${app.jwt.refresh-ttl}") Duration refreshTtl,
                            @Value("${app.jwt.absolute-ttl}") Duration absoluteTtl) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.absoluteTtl = absoluteTtl;
    }

    public TokenPair issue(String jiraAccountId, String email) {
        Instant now = Instant.now();
        // truncated to seconds: the encoded absolute_exp claim only has second-level
        // precision, so the in-memory value must match what a later decode round-trips
        Instant absoluteExpiry = now.plus(absoluteTtl).truncatedTo(ChronoUnit.SECONDS);
        return issueWithCap(jiraAccountId, email, absoluteExpiry);
    }

    /**
     * Decodes and fully validates a refresh token. Split from re-issuance so callers can
     * apply their own checks (e.g. server-side session invalidation) on the decoded token
     * before a new pair is minted. There is deliberately no combined
     * decode-and-reissue method: it would be a seam that skips those checks.
     */
    public Jwt decodeRefreshToken(String refreshTokenValue) {
        try {
            return refreshTokenDecoder.decode(refreshTokenValue);
        } catch (JwtValidationException e) {
            throw new SessionRefreshException(indicatesExpiry(e)
                    ? SessionRefreshException.Reason.SESSION_EXPIRED
                    : SessionRefreshException.Reason.INVALID_SESSION);
        } catch (JwtException e) {
            throw new SessionRefreshException(SessionRefreshException.Reason.INVALID_SESSION);
        }
    }

    public TokenPair reissueFrom(Jwt refreshJwt) {
        // decodeRefreshToken's validator chain (AbsoluteExpiryValidator) has already
        // guaranteed absolute_exp is present and unexpired — re-checking it here would be
        // unreachable code that can silently diverge from the validator
        Instant absoluteExpiry = Instant.ofEpochSecond(longClaim(refreshJwt, CLAIM_ABSOLUTE_EXP));
        return issueWithCap(refreshJwt.getSubject(), refreshJwt.getClaimAsString(CLAIM_EMAIL), absoluteExpiry);
    }

    /**
     * AbsoluteExpiryValidator reports expiry through a dedicated error code. Spring's own
     * JwtTimestampValidator only reports it in prose, so that half is matched on text and
     * pinned by JwtTokenServiceTest#expiredRefreshTokenIsClassifiedAsSessionExpired — if a
     * Spring upgrade rewords the message, that test fails rather than the classification
     * silently degrading to INVALID_SESSION.
     */
    private static boolean indicatesExpiry(JwtValidationException e) {
        return e.getErrors().stream().anyMatch(error ->
                AbsoluteExpiryValidator.ERROR_CODE_ABSOLUTE_EXPIRY.equals(error.getErrorCode())
                        || (error.getDescription() != null && error.getDescription().contains("expired")));
    }

    /**
     * Reads a numeric claim without assuming the JSON parser's concrete box type. A direct
     * assignment to Long would throw ClassCastException — not a JwtException, so it would
     * escape every catch here and surface as a 500 instead of a 401 — for any parser that
     * boxes an epoch-seconds value (which fits in an int) as Integer.
     */
    private static long longClaim(Jwt jwt, String name) {
        Number value = jwt.getClaim(name);
        return value.longValue();
    }

    private TokenPair issueWithCap(String jiraAccountId, String email, Instant absoluteExpiry) {
        Instant now = Instant.now();
        String access = encode(jiraAccountId, email, now, now.plus(accessTtl), TYPE_ACCESS, absoluteExpiry);
        String refresh = encode(jiraAccountId, email, now, now.plus(refreshTtl), TYPE_REFRESH, absoluteExpiry);
        return new TokenPair(access, refresh, absoluteExpiry);
    }

    private String encode(String subject, String email, Instant issuedAt, Instant expiresAt,
                           String tokenType, Instant absoluteExpiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ABSOLUTE_EXP, absoluteExpiry.getEpochSecond())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
