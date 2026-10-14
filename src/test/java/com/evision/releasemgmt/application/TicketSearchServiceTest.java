package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.auth.JiraAuthenticationException;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.ports.JiraTicketPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    @Mock
    private ReleaseRepository releaseRepository;

    private TicketSearchService service;

    @BeforeEach
    void setUp() {
        service = new TicketSearchService(accessContextService, fieldMetadataService, ticketPort,
                releaseRepository);
    }

    @Test
    void findByKeySearchesByKeyAndIsEmptyWhenJiraHasNoMatch() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), eq("key = CNV-9"), any())).thenReturn(List.of());

        assertThat(service.findByKey("CNV-9")).isEmpty();
    }

    @Test
    void resolvesCredentialAndAllNineFieldsBeforeSearchingWithComposedJql() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(List.of());

        service.search(TicketSearchCriteria.byProject("10023", "10891", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TicketField>> fields = ArgumentCaptor.forClass(Set.class);
        verify(fieldMetadataService).resolve(fields.capture());
        assertThat(fields.getValue()).doesNotContain(TicketField.STORY_POINTS).hasSize(TicketField.values().length - 1);

        verify(ticketPort).searchAll(eq(ACCESS),
                eq("project = 10023 AND sprint = 10891 AND statusCategory IN (\"Done\") ORDER BY key ASC"),
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

        List<TicketDetail> result = service.search(TicketSearchCriteria.byProject("10023", "10891", null));

        assertThat(result).containsExactlyElementsOf(fromPort);
    }

    @Test
    void credentialFailurePropagatesAndNeverReachesJira() {
        when(accessContextService.currentContext())
                .thenThrow(new JiraAuthenticationException(JiraAuthFailureReason.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> service.search(TicketSearchCriteria.byProject("10023", "10891", null)))
                .isInstanceOf(JiraAuthenticationException.class)
                .hasMessage(JiraAuthFailureReason.INVALID_CREDENTIALS.name());

        verify(ticketPort, never()).searchAll(any(), anyString(), any());
    }

    @Test
    void fieldResolutionFailurePropagatesAndNeverReachesJira() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any()))
                .thenThrow(new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR));

        assertThatThrownBy(() -> service.search(TicketSearchCriteria.byProject("10023", "10891", null)))
                .isInstanceOf(JiraTicketReadException.class);

        verify(ticketPort, never()).searchAll(any(), anyString(), any());
    }

    @Test
    void filtersOutTicketsAlreadyOnTheTargetRelease() {
        UUID releaseId = UUID.randomUUID();
        List<TicketDetail> fromPort = List.of(ticket("CNV-1"), ticket("CNV-2"), ticket("CNV-3"));
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(fromPort);
        when(releaseRepository.findTicketKeys(releaseId)).thenReturn(List.of("CNV-2"));

        List<TicketDetail> result =
                service.search(TicketSearchCriteria.byProject("10023", "10891", null), releaseId);

        assertThat(result).extracting(TicketDetail::key).containsExactly("CNV-1", "CNV-3");
    }

    @Test
    void theSameSearchOmitsATicketWithAReleaseScopeAndReturnsItWithout() {
        UUID releaseId = UUID.randomUUID();
        List<TicketDetail> fromPort = List.of(ticket("CNV-1"), ticket("CNV-2"));
        TicketSearchCriteria criteria = TicketSearchCriteria.byProject("10023", "10891", null);
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(fromPort);
        when(releaseRepository.findTicketKeys(releaseId)).thenReturn(List.of("CNV-2"));

        List<TicketDetail> scoped = service.search(criteria, releaseId);
        List<TicketDetail> unscoped = service.search(criteria);

        assertThat(scoped).extracting(TicketDetail::key).containsExactly("CNV-1");
        assertThat(unscoped).extracting(TicketDetail::key).containsExactly("CNV-1", "CNV-2");

        ArgumentCaptor<String> jql = ArgumentCaptor.forClass(String.class);
        verify(ticketPort, org.mockito.Mockito.times(2)).searchAll(any(), jql.capture(), any());
        List<String> capturedJql = jql.getAllValues();
        assertThat(capturedJql.get(0)).isEqualTo(capturedJql.get(1));
        assertThat(capturedJql.get(0)).doesNotContain(releaseId.toString());
    }

    @Test
    void noReleaseScopeNeverQueriesReleaseMembership() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(List.of());

        service.search(TicketSearchCriteria.byProject("10023", "10891", null));

        verify(releaseRepository, never()).findTicketKeys(any());
    }

    @Test
    void anUnknownReleaseScopeFiltersNothing() {
        UUID releaseId = UUID.randomUUID();
        List<TicketDetail> fromPort = List.of(ticket("CNV-1"), ticket("CNV-2"), ticket("CNV-3"));
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(fromPort);
        when(releaseRepository.findTicketKeys(releaseId)).thenReturn(List.of());

        List<TicketDetail> result =
                service.search(TicketSearchCriteria.byProject("10023", "10891", null), releaseId);

        assertThat(result).extracting(TicketDetail::key).containsExactly("CNV-1", "CNV-2", "CNV-3");
    }

    @Test
    void aMovedIssueIsNotHiddenUnderItsNewKey() {
        // ReleaseService:81-83 - an issue that moved projects is requested as OLD-1 and comes
        // back from Jira as NEW-5, so a filter keyed on the release's stored ticket_key cannot
        // hide it under its new key. The release genuinely does not contain NEW-5.
        UUID releaseId = UUID.randomUUID();
        List<TicketDetail> fromPort = List.of(ticket("NEW-5"));
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(fromPort);
        when(releaseRepository.findTicketKeys(releaseId)).thenReturn(List.of("OLD-1"));

        List<TicketDetail> result =
                service.search(TicketSearchCriteria.byProject("10023", "10891", null), releaseId);

        assertThat(result).extracting(TicketDetail::key).containsExactly("NEW-5");
    }

    @Test
    void aTicketOnADifferentReleaseIsStillVisible() {
        // D-05.2-01: the filter is per-target-release, never global. The schema's
        // release_ticket_item_key_unique (release_id, ticket_key) is per-release precisely so a
        // hotfix can ship in two releases at once - switching this to a global filter would pass
        // the rest of the suite, which is why this test exists.
        UUID releaseA = UUID.randomUUID();
        UUID releaseB = UUID.randomUUID();
        List<TicketDetail> fromPort = List.of(ticket("CNV-2"));
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(fromPort);
        // releaseA is never queried by this call - present only to document that its membership
        // has no bearing on a search scoped to releaseB.
        org.mockito.Mockito.lenient().when(releaseRepository.findTicketKeys(releaseA)).thenReturn(List.of("CNV-2"));
        when(releaseRepository.findTicketKeys(releaseB)).thenReturn(List.of());

        List<TicketDetail> result =
                service.search(TicketSearchCriteria.byProject("10023", "10891", null), releaseB);

        assertThat(result).extracting(TicketDetail::key).containsExactly("CNV-2");
    }

    private static TicketDetail ticket(String key) {
        return new TicketDetail(key, "Summary " + key, "Bug", "Done", "Fixed", null, "Reporter",
                List.of("1.0.0"), null, "", List.of(), 0, null, null, null, null, false);
    }

    @Test
    void addsTheOptionalStoryPointsFieldIdToTheSearchedFieldMap() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(fieldMetadataService.storyPointsFieldId()).thenReturn(Optional.of("customfield_10016"));
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(List.of());

        service.search(TicketSearchCriteria.byProject("10023", "10891", null));

        Map<TicketField, String> expected = new HashMap<>(FIELD_IDS);
        expected.put(TicketField.STORY_POINTS, "customfield_10016");
        verify(ticketPort).searchAll(eq(ACCESS), anyString(), eq(expected));
    }

    @Test
    void searchesWithTheResolvedMapAloneWhenStoryPointsAreUnresolvable() {
        when(accessContextService.currentContext()).thenReturn(ACCESS);
        when(fieldMetadataService.resolve(any())).thenReturn(FIELD_IDS);
        when(ticketPort.searchAll(any(), anyString(), any())).thenReturn(List.of());

        service.search(TicketSearchCriteria.byProject("10023", "10891", null));

        verify(ticketPort).searchAll(eq(ACCESS), anyString(), eq(FIELD_IDS));
    }
}
