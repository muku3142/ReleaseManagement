package com.evision.releasemgmt.adapters.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
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

    public TokenPair refresh(String refreshTokenValue) {
        Jwt jwt;
        try {
            jwt = refreshTokenDecoder.decode(refreshTokenValue);
        } catch (JwtValidationException e) {
            boolean expired = e.getErrors().stream()
                    .map(OAuth2Error::getDescription)
                    .filter(description -> description != null)
                    .anyMatch(description -> description.contains("expired")
                            || description.contains("Session absolute expiry reached"));
            throw new SessionRefreshException(expired
                    ? SessionRefreshException.Reason.SESSION_EXPIRED
                    : SessionRefreshException.Reason.INVALID_SESSION);
        } catch (JwtException e) {
            throw new SessionRefreshException(SessionRefreshException.Reason.INVALID_SESSION);
        }

        Long absoluteExpEpoch = jwt.getClaim(CLAIM_ABSOLUTE_EXP);
        if (absoluteExpEpoch == null) {
            throw new SessionRefreshException(SessionRefreshException.Reason.INVALID_SESSION);
        }
        Instant absoluteExpiry = Instant.ofEpochSecond(absoluteExpEpoch);
        if (Instant.now().isAfter(absoluteExpiry)) {
            throw new SessionRefreshException(SessionRefreshException.Reason.SESSION_EXPIRED);
        }

        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        return issueWithCap(jwt.getSubject(), email, absoluteExpiry);
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
