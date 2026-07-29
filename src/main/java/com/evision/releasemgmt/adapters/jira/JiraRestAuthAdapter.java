package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.auth.JiraIdentity;
import com.evision.releasemgmt.ports.JiraAuthPort;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Profile("!stub-jira")
public class JiraRestAuthAdapter implements JiraAuthPort {

    private final RestClient restClient;

    public JiraRestAuthAdapter(RestClient jiraRestClient) {
        this.restClient = jiraRestClient;
    }

    @Override
    public JiraIdentity validate(String siteBaseUrl, String email, String apiToken) {
        String basic = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        try {
            JiraMyselfResponse body = restClient.get()
                    .uri(siteBaseUrl + "/rest/api/3/myself")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JiraMyselfResponse.class);
            if (body == null || body.accountId() == null) {
                throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_ERROR);
            }
            return new JiraIdentity(body.accountId(), body.emailAddress(), body.displayName());
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_FORBIDDEN);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_RATE_LIMITED);
        } catch (HttpServerErrorException e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_ERROR);
        } catch (ResourceAccessException e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_UNREACHABLE);
        } catch (RestClientException e) {
            throw new JiraAuthenticationException(JiraAuthFailureReason.JIRA_ERROR);
        }
    }
}
