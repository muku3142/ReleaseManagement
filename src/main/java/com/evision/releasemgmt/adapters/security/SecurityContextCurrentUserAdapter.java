package com.evision.releasemgmt.adapters.security;

import com.evision.releasemgmt.ports.CurrentUserPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityContextCurrentUserAdapter implements CurrentUserPort {

    @Override
    public Optional<String> currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.ofNullable(jwtAuthentication.getToken().getSubject());
        }
        return Optional.empty();
    }
}
