package com.evision.releasemgmt.config;

import com.evision.releasemgmt.adapters.security.AbsoluteExpiryValidator;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.adapters.security.TokenTypeValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a >= 256-bit key

    @Bean
    public SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String base64Secret) {
        byte[] raw = Base64.getDecoder().decode(base64Secret);
        if (raw.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "RM_JWT_SECRET must decode to at least 32 bytes for HS256; got " + raw.length);
        }
        return new SecretKeySpec(raw, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    @Primary
    JwtDecoder accessTokenDecoder(SecretKey jwtSigningKey) {
        return decoder(jwtSigningKey, JwtTokenService.TYPE_ACCESS);
    }

    @Bean
    JwtDecoder refreshTokenDecoder(SecretKey jwtSigningKey) {
        return decoder(jwtSigningKey, JwtTokenService.TYPE_REFRESH);
    }

    private JwtDecoder decoder(SecretKey key, String expectedType) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new TokenTypeValidator(expectedType),
                new AbsoluteExpiryValidator()));
        return decoder;
    }
}
