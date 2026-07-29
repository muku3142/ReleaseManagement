package com.evision.releasemgmt.adapters.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedType;

    public TokenTypeValidator(String expectedType) {
        this.expectedType = expectedType;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (expectedType.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Unexpected token_type", null));
    }
}
