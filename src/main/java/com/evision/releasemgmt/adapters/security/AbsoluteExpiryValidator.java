package com.evision.releasemgmt.adapters.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

public class AbsoluteExpiryValidator implements OAuth2TokenValidator<Jwt> {

    /** Distinct code so callers classify absolute expiry without matching on prose. */
    public static final String ERROR_CODE_ABSOLUTE_EXPIRY = "session_absolute_expiry";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // Number, not Long: epoch seconds fit in an int, so a JSON parser is free to box
        // the value as Integer and a direct Long cast would throw ClassCastException
        Number absoluteExp = jwt.getClaim(JwtTokenService.CLAIM_ABSOLUTE_EXP);
        if (absoluteExp == null) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing absolute_exp claim", null));
        }
        if (Instant.now().isAfter(Instant.ofEpochSecond(absoluteExp.longValue()))) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(ERROR_CODE_ABSOLUTE_EXPIRY, "Session absolute expiry reached", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
