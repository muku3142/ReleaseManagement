package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusCategoryServiceTest {

    private final JiraIssueHistoryPort port = mock(JiraIssueHistoryPort.class);
    private final StatusCategoryService service = new StatusCategoryService(port);
    private final JiraAccessContext access = new JiraAccessContext("http://jira", "acc", "rm@acme.com", "tok");

    @Test
    void fetchesOnceThenServesKnownIdsFromTheCache() {
        when(port.statusCategories(access)).thenReturn(Map.of("1", StatusCategory.TO_DO, "2", StatusCategory.DONE));

        assertThat(service.categoriesFor(access, Set.of("1"))).containsOnlyKeys("1");
        assertThat(service.categoriesFor(access, Set.of("2"))).containsEntry("2", StatusCategory.DONE);

        verify(port, times(1)).statusCategories(access);
    }

    @Test
    void refreshesExactlyOnceForAnUnknownIdAndPicksItUp() {
        when(port.statusCategories(access)).thenReturn(Map.of("1", StatusCategory.TO_DO))
                .thenReturn(Map.of("1", StatusCategory.TO_DO, "3", StatusCategory.IN_PROGRESS));
        service.categoriesFor(access, Set.of("1"));

        assertThat(service.categoriesFor(access, Set.of("1", "3"))).containsEntry("3", StatusCategory.IN_PROGRESS);

        verify(port, times(2)).statusCategories(access);
    }

    @Test
    void anIdStillUnknownAfterTheRefreshIsAbsent() {
        when(port.statusCategories(access)).thenReturn(Map.of("1", StatusCategory.TO_DO));

        assertThat(service.categoriesFor(access, Set.of("1", "9"))).containsOnlyKeys("1");

        verify(port, times(1)).statusCategories(access);
    }
}
