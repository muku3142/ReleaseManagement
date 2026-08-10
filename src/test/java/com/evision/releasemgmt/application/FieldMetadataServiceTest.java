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
            new JiraField("summary", "Summary", false),
            new JiraField("issuetype", "Issue Type", false),
            new JiraField("status", "Status", false),
            new JiraField("resolution", "Resolution", false),
            new JiraField("assignee", "Assignee", false),
            new JiraField("reporter", "Reporter", false),
            new JiraField("description", "Description", false),
            new JiraField("comment", "Comment", false),
            new JiraField("fixVersions", "Fix versions", false),
            new JiraField("components", "Components", false));

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
        assertThat(resolved.get(TicketField.COMPONENT)).isEqualTo("components");
    }

    @Test
    void resolutionIsCaseAndWhitespaceInsensitiveOnDisplayName() {
        stubAccess();
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(List.of(new JiraField("fixVersions", "  fix VERSIONS  ", false)));

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
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(List.of(new JiraField("issuetype", "Issue Type", false)));

        assertThatThrownBy(() -> service.resolve(Set.of(TicketField.SUMMARY)))
                .isInstanceOf(JiraTicketReadException.class);
        verify(fieldMetadataPort, times(2)).fetchAllFields(any());
    }

    @Test
    void duplicateNameNotRequestedDoesNotThrow() {
        stubAccess();
        List<JiraField> withCollision = List.of(
                new JiraField("summary", "Summary", false),
                new JiraField("issuetype", "Issue Type", false),
                new JiraField("status", "Status", false),
                new JiraField("resolution", "Resolution", false),
                new JiraField("assignee", "Assignee", false),
                new JiraField("reporter", "Reporter", false),
                new JiraField("description", "Description", false),
                new JiraField("comment", "Comment", false),
                new JiraField("fixVersions", "Fix versions", false),
                new JiraField("components", "Components", false),
                new JiraField("customfield_10001", "Story Points", true),
                new JiraField("customfield_10002", "Story Points", true));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(withCollision);

        Map<TicketField, String> resolved = service.resolve(Set.of(TicketField.values()));

        assertThat(resolved).hasSize(10);
    }

    @Test
    void systemFieldWinsWhenCustomFieldsShadowItsName() {
        stubAccess();
        List<JiraField> withCollision = List.of(
                new JiraField("summary", "Summary", false),
                new JiraField("customfield_10099", "Summary", true));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(withCollision);

        Map<TicketField, String> resolved = service.resolve(Set.of(TicketField.SUMMARY));

        assertThat(resolved).containsEntry(TicketField.SUMMARY, "summary");
    }

    @Test
    void resolvesTheEvisionTenantsThreeWayResolutionCollision() {
        // Verbatim from the live tenant: 'Resolution' resolved to three ids and blocked every
        // search with JIRA_ERROR until the system field was allowed to win.
        stubAccess();
        List<JiraField> tenantFields = List.of(
                new JiraField("summary", "Summary", false),
                new JiraField("issuetype", "Issue Type", false),
                new JiraField("status", "Status", false),
                new JiraField("resolution", "Resolution", false),
                new JiraField("customfield_11864", "Resolution", true),
                new JiraField("customfield_11863", "Resolution", true),
                new JiraField("assignee", "Assignee", false),
                new JiraField("reporter", "Reporter", false),
                new JiraField("description", "Description", false),
                new JiraField("comment", "Comment", false),
                new JiraField("fixVersions", "Fix versions", false),
                new JiraField("components", "Components", false));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(tenantFields);

        Map<TicketField, String> resolved = service.resolve(Set.of(TicketField.values()));

        assertThat(resolved).hasSize(10);
        assertThat(resolved).containsEntry(TicketField.RESOLUTION, "resolution");
    }

    @Test
    void customOnlyCollisionStillRefusesToGuess() {
        stubAccess();
        // No system field to fall back on: this is the silent-wrong-field risk the guard exists
        // for, and it must still fail rather than pick one.
        List<JiraField> ambiguous = List.of(
                new JiraField("customfield_10098", "Summary", true),
                new JiraField("customfield_10099", "Summary", true));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(ambiguous);

        assertThatThrownBy(() -> service.resolve(Set.of(TicketField.SUMMARY)))
                .isInstanceOf(JiraTicketReadException.class);
    }

    @Test
    void reportsEveryUnresolvableFieldNotJustTheFirst() {
        stubAccess();
        // Only three of the nine names exist. The thrown failure must not stop at the first.
        List<JiraField> sparse = List.of(
                new JiraField("summary", "Summary", false),
                new JiraField("issuetype", "Issue Type", false),
                new JiraField("status", "Status", false));
        when(fieldMetadataPort.fetchAllFields(any())).thenReturn(sparse);

        assertThatThrownBy(() -> service.resolve(Set.of(TicketField.values())))
                .isInstanceOf(JiraTicketReadException.class);
        // One refresh attempt per missing name, then a single throw — not ten separate calls.
        verify(fieldMetadataPort, times(8)).fetchAllFields(any());
    }
}
