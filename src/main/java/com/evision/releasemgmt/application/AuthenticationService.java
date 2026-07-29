package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.adapters.security.TokenPair;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import com.evision.releasemgmt.ports.JiraAuthPort;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Login orchestration: validate against Jira live, persist the encrypted credential only
 * after validation succeeds, and issue the token pair. Never mints a session or writes a
 * credential for a token Jira has not itself accepted (AUTH-01).
 */
@Service
public class AuthenticationService {

    private final JiraSiteConfigService jiraSiteConfigService;
    private final JiraAuthPort jiraAuthPort;
    private final JiraCredentialRepository credentialRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtDecoder accessTokenDecoder;

    public AuthenticationService(JiraSiteConfigService jiraSiteConfigService,
                                  JiraAuthPort jiraAuthPort,
                                  JiraCredentialRepository credentialRepository,
                                  JwtTokenService jwtTokenService,
                                  @Qualifier("accessTokenDecoder") JwtDecoder accessTokenDecoder) {
        this.jiraSiteConfigService = jiraSiteConfigService;
        this.jiraAuthPort = jiraAuthPort;
        this.credentialRepository = credentialRepository;
        this.jwtTokenService = jwtTokenService;
        this.accessTokenDecoder = accessTokenDecoder;
    }

    public record SignedInUser(String accountId, String email, Instant absoluteExpiry) {
    }

    public record LoginResult(TokenPair pair, SignedInUser user) {
    }

    public LoginResult login(String email, String apiToken) {
        String siteBaseUrl = jiraSiteConfigService.get().baseUrl();
        JiraIdentity identity = jiraAuthPort.validate(siteBaseUrl, email, apiToken);
        // store the submitted email, not identity.email() - Jira Cloud's /myself may
        // return a null emailAddress depending on account email-visibility settings, and
        // the stored email is what reconstructs Basic base64(email:token) for later calls
        credentialRepository.upsert(identity.accountId(), email, apiToken);
        TokenPair pair = jwtTokenService.issue(identity.accountId(), email);
        return new LoginResult(pair, new SignedInUser(identity.accountId(), email, pair.absoluteExpiry()));
    }

    public LoginResult refresh(String refreshTokenValue) {
        TokenPair pair = jwtTokenService.refresh(refreshTokenValue);
        Jwt accessJwt = accessTokenDecoder.decode(pair.accessToken());
        String accountId = accessJwt.getSubject();
        String email = accessJwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL);
        return new LoginResult(pair, new SignedInUser(accountId, email, pair.absoluteExpiry()));
    }
}
