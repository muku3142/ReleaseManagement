package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.security.JwtTokenService;
import com.evision.releasemgmt.adapters.security.SessionRefreshException;
import com.evision.releasemgmt.adapters.security.TokenPair;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraCredential;
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
        Jwt refreshJwt = jwtTokenService.decodeRefreshToken(refreshTokenValue);
        rejectIfSessionsInvalidated(refreshJwt);
        TokenPair pair = jwtTokenService.reissueFrom(refreshJwt);
        Jwt accessJwt = accessTokenDecoder.decode(pair.accessToken());
        String accountId = accessJwt.getSubject();
        String email = accessJwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL);
        return new LoginResult(pair, new SignedInUser(accountId, email, pair.absoluteExpiry()));
    }

    /**
     * Ends the session server-side as well as in the browser. Without this, clearing the
     * cookies would leave a captured refresh token usable for its full TTL and chainable up
     * to the absolute cap — logout would be a client-side gesture, not a session teardown.
     *
     * <p>Reads the refresh cookie rather than the authenticated principal on purpose: the
     * access token may already have expired while the refresh token is still live, and that
     * is precisely the case that most needs invalidating.
     */
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        try {
            Jwt refreshJwt = jwtTokenService.decodeRefreshToken(refreshTokenValue);
            credentialRepository.markSessionsInvalidated(refreshJwt.getSubject(), Instant.now());
        } catch (SessionRefreshException e) {
            // already invalid or expired — nothing left to invalidate
        }
    }

    private void rejectIfSessionsInvalidated(Jwt refreshJwt) {
        Instant issuedAt = refreshJwt.getIssuedAt();
        Instant invalidatedAt = credentialRepository
                .findSessionsInvalidatedAt(refreshJwt.getSubject())
                .orElse(null);
        if (invalidatedAt != null && issuedAt != null && issuedAt.isBefore(invalidatedAt)) {
            throw new SessionRefreshException(SessionRefreshException.Reason.INVALID_SESSION);
        }
    }

    /**
     * Revalidates a replacement token against the configured Jira site before storing
     * anything (D-15), refuses to let one account's credential be overwritten by a token
     * that authenticates as a different Jira account (AUTH-04, T-07-08), and otherwise
     * upserts in place so created_at and every other record survive.
     */
    public void replaceToken(String accountId, String apiToken) {
        JiraCredential existing = credentialRepository.findByAccountId(accountId)
                .orElseThrow(() -> new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));
        String siteBaseUrl = jiraSiteConfigService.get().baseUrl();
        JiraIdentity identity = jiraAuthPort.validate(siteBaseUrl, existing.email(), apiToken);
        if (!accountId.equals(identity.accountId())) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.ACCOUNT_MISMATCH);
        }
        credentialRepository.upsert(accountId, existing.email(), apiToken);
    }
}
