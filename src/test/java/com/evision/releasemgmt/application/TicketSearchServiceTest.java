package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.ports.JiraTicketPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketSearchServiceTest {

    private static final JiraAccessContext ACCESS =
            new JiraAccessContext("https://acme.atlassian.net", "acct-1", "rm@acme.com", "tok-123");

    private static final Map<TicketField, String> FIELD_IDS = Map.of(
            TicketField.SUMMARY, "summary",
            TicketField.ISSUE_TYPE, "issuetype",
            TicketField.STATUS, "status",
            TicketField.RESOLUTION, "resolution",
            TicketField.ASSIGNEE, "assignee",
            TicketField.REPORTER, "reporter",
            TicketField.DESCRIPTION, "description",
            TicketField.COMMENT, "comment",
            TicketField.FIX_VERSIONS, "fixVersions");

    @Mock
    private JiraAccessContextService accessContextService;

    @Mock
    private FieldMetadataService fieldMetadataService;

    @Mock
    private JiraTicketPort ticketPort;

    private TicketSearchService service;

    @BeforeEach
    void setUp() {
        service = new TicketSearchService(accessContextService, fieldMetadataService, ticketPort);
    }

    @Test
    void resolvesCredentialAndAllNineFieldsBeforeSearchingWithComposedJql() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(List.of());

        service.search(TicketSearchCriteria.byFixVersion("10023", "10891", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TicketField>> fields = ArgumentCaptor.forClass(Set.class);
        verify(fieldMetadataService).resolve(fields.capture());
        assertThat(fields.getValue()).containsExactlyInAnyOrder(TicketField.values());

        verify(ticketPort).searchAll(eq(ACCESS),
                eq("project = 10023 AND fixVersion = 10891 AND statusCategory IN (\"Done\") ORDER BY key ASC"),
                eq(FIELD_IDS));

        // AUTH-02 is not an afterthought in the call order: the credential is resolved before
        // any Jira read is issued.
        InOrder order = inOrder(accessContextService, ticketPort);
        order.verify(accessContextService).currentContext();
        order.verify(ticketPort).searchAll(any(), anyString(), any());
    }

    @Test
    void forwardsRawJqlVerbatim() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(List.of());

        service.search(TicketSearchCriteria.byJql("project = CNV AND status = Done"));

        verify(ticketPort).searchAll(eq(ACCESS), eq("project = CNV AND status = Done"), eq(FIELD_IDS));
    }

    @Test
    void returnsThePortsListUnmodifiedAndInOrder() {
        List<TicketDetail> fromPort = List.of(ticket("CNV-1"), ticket("CNV-2"), ticket("CNV-3"));
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(fromPort);

        List<TicketDetail> result = service.search(TicketSearchCriteria.byFixVersion("10023", "10891", null));

        assertThat(result).containsExactlyElementsOf(fromPort);
    }

    @Test
    void credentialFailurePropagatesAndNeverReachesJira() {
        when(accessContextService.currentContext())
                .thenThrow(new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> service.search(TicketSearchCriteria.byFixVersion("10023", "10891", null)))
                .isInstanceOf(JiraAuthenticationException.class)
                .hasMessage(JiraAuthFailureReason.INVALID_CREDENTIALS.name());

        verify(ticketPort, never()).searchAll(any(), anyString(), any());
    }

    @Test
    void fieldResolutionFailurePropagatesAndNeverReachesJira() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR));

        assertThatThrownBy(() -> service.search(TicketSearchCriteria.byFixVersion("10023", "10891", null)))
                .isInstanceOf(JiraTicketReadException.class);

        verify(ticketPort, never()).searchAll(any(), anyString(), any());
    }

    private static TicketDetail ticket(String key) {
        return new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed", null, "Reporter",
                List.of("1.0.0"), "", List.of(), 0);
    }
}
