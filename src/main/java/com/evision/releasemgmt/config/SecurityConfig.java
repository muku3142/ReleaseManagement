package com.evision.releasemgmt.config;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             JwtDecoder accessTokenDecoder,
                                             CookieBearerTokenResolver cookieBearerTokenResolver,
                                             ProblemDetailAuthenticationEntryPoint problemDetailEntryPoint) throws Exception {
        http
                // oauth2ResourceServer() unconditionally registers an additional CSRF-ignore
                // rule for any request a bearer token can be resolved from (assuming the token
                // travels via the Authorization header, immune to browser auto-attachment).
                // Our bearer token is cookie-carried, so this still exempts authenticated writes
                // from csrf.spa() in practice - see 01-05-SUMMARY.md Threat Flags for the
                // residual-risk analysis (SameSite=Strict is the actual control for those
                // requests). This explicit matcher keeps csrf.spa() fully enforced for any
                // request csrf.spa() alone would still apply to (e.g. no bearer cookie at all).
                .csrf(csrf -> csrf.spa().requireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(cookieBearerTokenResolver)
                        .authenticationEntryPoint(problemDetailEntryPoint)
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailEntryPoint)
                        .accessDeniedHandler(problemDetailEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/settings/jira-site").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // any further actuator endpoint must be authenticated: exposure is
                        // config-driven, so widening management.endpoints.web.exposure.include
                        // must not silently publish env/beans/etc.
                        .requestMatchers("/actuator/**").authenticated()
                        // The only surface left is the SPA — index.html, static assets and
                        // deep links forwarded by SpaForwardingController — all of which are
                        // GET/HEAD. Everything else denies by default, so a future
                        // non-/api endpoint cannot become public merely by being forgotten.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
