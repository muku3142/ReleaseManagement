package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.AuthCookieWriter;
import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.adapters.security.SessionRefreshException;
import com.evision.releasemgmt.adapters.web.dto.LoginRequest;
import com.evision.releasemgmt.adapters.web.dto.SessionResponse;
import com.evision.releasemgmt.application.AuthenticationService;
import com.evision.releasemgmt.application.AuthenticationService.LoginResult;
import com.evision.releasemgmt.application.AuthenticationService.SignedInUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AuthCookieWriter cookieWriter;

    public AuthController(AuthenticationService authenticationService, AuthCookieWriter cookieWriter) {
        this.authenticationService = authenticationService;
        this.cookieWriter = cookieWriter;
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = authenticationService.login(request.email(), request.apiToken());
        cookieWriter.write(response, result.pair());
        return toResponse(result.user());
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(
            @CookieValue(name = AuthCookieWriter.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SessionRefreshException(SessionRefreshException.Reason.INVALID_SESSION);
        }
        LoginResult result = authenticationService.refresh(refreshToken);
        cookieWriter.write(response, result.pair());
        return toResponse(result.user());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        cookieWriter.clear(response);
    }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        Long absoluteExpEpoch = jwt.getClaim(JwtTokenService.CLAIM_ABSOLUTE_EXP);
        return new SessionResponse(
                jwt.getSubject(),
                jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL),
                Instant.ofEpochSecond(absoluteExpEpoch));
    }

    private SessionResponse toResponse(SignedInUser user) {
        return new SessionResponse(user.accountId(), user.email(), user.absoluteExpiry());
    }
}
