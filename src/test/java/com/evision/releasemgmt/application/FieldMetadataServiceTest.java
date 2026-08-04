package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.JiraField;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.ports.JiraFieldMetadataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldMetadataServiceTest {

    @Mock
    private JiraAccessContextService accessContextService;

    @Mock
    private JiraFieldMetadataPort fieldMetadataPort;

    private FieldMetadataService service;

    private static final List<JiraField> SYSTEM_FIELDS = List.of(
            new JiraField("summary", "Summary"),
            new JiraField("issuetype", "Issue Type"),
            new JiraField("status", "Status"),
            new JiraField("resolution", "Resolution"),
            new JiraField("assignee", "Assignee"),
            new JiraField("reporter", "Reporter"),
            new JiraField("description", "Description"),
            new JiraField("comment", "Comment"),
            new JiraField("fixVersions", "Fix versions"));

    @BeforeEach
    void setUp() {
        service = new FieldMetadataService(accessContextService, fieldMetadataPort);
    }

    private JiraAccessContext stubAccess() {
        JiraAccessContext access = new JiraAccessContext("https://example.atlassian.net", "acct-1", "rm@acme.com", "tok-123");
        when(accessContextService.currentContext()).thenReturn(access);
        return access;
    }

    @Test
    void resolvesAllNineFieldsToTheirSystemIds() {
        stubAccess();
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(SYSTEM_FIELDS);

        Map<TicketField, String> resolved = service.resolve(Set.of(TicketField.values()));

        assertThat(resolved.get(TicketField.SUMMARY)).isEqualTo("summary");
        assertThat(resolved.get(TicketField.ISSUE_TYPE)).isEqualTo("issuetype");
        assertThat(resolved.get(TicketField.STATUS)).isEqualTo("status");
        assertThat(resolved.get(TicketField.RESOLUTION)).isEqualTo("resolution");
        assertThat(resolved.get(TicketField.ASSIGNEE)).isEqualTo("assignee");
        assertThat(resolved.get(TicketField.REPORTER)).isEqualTo("reporter");
        assertThat(resolved.get(TicketField.DESCRIPTION)).isEqualTo("description");
        assertThat(resolved.get(TicketField.COMMENT)).isEqualTo("comment");
        assertThat(resolved.get(TicketField.FIX_VERSIONS)).isEqualTo("fixVersions");
    }

    @Test
    void resolutionIsCaseAndWhitespaceInsensitiveOnDisplayName() {
        stubAccess();
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(List.of(new JiraField("fixVersions", "  fix VERSIONS  ")));

        Map<TicketField, String> resolved = service.resolve(Set.of(TicketField.FIX_VERSIONS));

        assertThat(resolved.get(TicketField.FIX_VERSIONS)).isEqualTo("fixVersions");
    }

    @Test
    void portIsCalledOnceAcrossTwoResolveCalls() {
        stubAccess();
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(SYSTEM_FIELDS);

        service.resolve(Set.of(TicketField.SUMMARY));
        service.resolve(Set.of(TicketField.STATUS));

        verify(fieldMetadataPort, times(1)).fetchAllFields(any());
    }

    @Test
    void nameAbsentFromMetadataTriggersExactlyOneRefreshThenThrows() {
        stubAccess();
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(List.of(new JiraField("issuetype", "Issue Type")));

        assertThatThrownBy(() -> service.resolve(Set.of(TicketField.SUMMARY)))
                .isInstanceOf(JiraTicketReadException.class);
        verify(fieldMetadataPort, times(2)).fetchAllFields(any());
    }

    @Test
    void duplicateNameNotRequestedDoesNotThrow() {
        stubAccess();
        List<JiraField> withCollision = List.of(
                new JiraField("summary", "Summary"),
                new JiraField("issuetype", "Issue Type"),
                new JiraField("status", "Status"),
                new JiraField("resolution", "Resolution"),
                new JiraField("assignee", "Assignee"),
                new JiraField("reporter", "Reporter"),
                new JiraField("description", "Description"),
                new JiraField("comment", "Comment"),
                new JiraField("fixVersions", "Fix versions"),
                new JiraField("customfield_10001", "Story Points"),
                new JiraField("customfield_10002", "Story Points"));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(withCollision);

        Map<TicketField, String> resolved = service.resolve(Set.of(TicketField.values()));

        assertThat(resolved).hasSize(9);
    }

    @Test
    void duplicateNameRequestedThrows() {
        stubAccess();
        List<JiraField> withCollision = List.of(
                new JiraField("summary", "Summary"),
                new JiraField("customfield_10099", "Summary"));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(withCollision);

        assertThatThrownBy(() -> service.resolve(Set.of(TicketField.SUMMARY)))
                .isInstanceOf(JiraTicketReadException.class);
    }
}
