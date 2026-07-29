package com.evision.releasemgmt.adapters.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

public class AbsoluteExpiryValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long absoluteExp = jwt.getClaim(JwtTokenService.CLAIM_ABSOLUTE_EXP);
        if (absoluteExp == null) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing absolute_exp claim", null));
        }
        if (Instant.now().isAfter(Instant.ofEpochSecond(absoluteExp))) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Session absolute expiry reached", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
