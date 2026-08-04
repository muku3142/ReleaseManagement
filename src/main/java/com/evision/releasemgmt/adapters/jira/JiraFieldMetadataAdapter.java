package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraField;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.ports.JiraFieldMetadataPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class JiraFieldMetadataAdapter implements JiraFieldMetadataPort {

    private final RestClient restClient;

    public JiraFieldMetadataAdapter(RestClient jiraRestClient) {
        this.restClient = jiraRestClient;
    }

    @Override
    public List<JiraField> fetchAllFields(JiraAccessContext access) {
        String basic = Base64.getEncoder()
                .encodeToString((access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
        try {
            JiraFieldPayload[] body = restClient.get()
                    .uri(access.siteBaseUrl() + "/rest/api/3/field")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JiraFieldPayload[].class);
            if (body == null) {
                return List.of();
            }
            List<JiraField> fields = new ArrayList<>(body.length);
            for (JiraFieldPayload payload : body) {
                fields.add(new JiraField(payload.id(), payload.name(), payload.custom()));
            }
            return fields;
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_FORBIDDEN);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED);
        } catch (HttpServerErrorException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        } catch (ResourceAccessException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE);
        } catch (RestClientException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
    }
}
