package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.dashboard.TicketChurn;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketsToolTest {

    private static final String CANARY = "CANARY-TEXT";

    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final DashboardService dashboardService = mock(DashboardService.class);
    private final ReleaseRef releaseRef = mock(ReleaseRef.class);
    private final TicketsTool tool = new TicketsTool(releaseService, dashboardService, releaseRef);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static ReleaseItem item(String key) {
        TicketDetail ticket = new TicketDetail(key, "Fix " + key, "Bug", "Done", null, "ann", "bob", List.of("2.4"),
                null, CANARY, List.of(CANARY), 3, "1", Instant.EPOCH, StatusCategory.DONE, null, false);
        return new ReleaseItem(ticket, Instant.EPOCH, CodeLinks.notAttempted());
    }

    private static Release release(UUID id, List<ReleaseItem> items) {
        return new Release(id, "Payments", "2.4", ReleaseStatus.OPEN, "PAY", Instant.EPOCH, "a", null, null, null,
                null, items, List.of());
    }

    @Test
    void keyReturnsStructuredFieldsAndKeepsNullChurnAsNull() {
        UUID id = UUID.randomUUID();
        when(releaseService.releasesContaining("PAY-1")).thenReturn(List.of(new ReleaseSummary(id, "Payments", "2.4",
                ReleaseStatus.OPEN, "PAY", Instant.EPOCH, "a", null, null, null, null, 1)));
        when(releaseService.get(id)).thenReturn(release(id, List.of(item("PAY-1"))));
        when(dashboardService.ticketChurn(id)).thenReturn(List.of(new TicketChurn("PAY-1", null, 2L, null, false, true)));

        JsonNode json = mapper.valueToTree(tool.call(Map.of("key", "PAY-1")).result());

        JsonNode row = json.get("tickets").get(0);
        assertThat(row.get("storyPoints").isNull()).isTrue();
        assertThat(row.get("churn").get("commitsToDone").isNull()).isTrue();
        assertThat(row.get("churn").get("postDoneCommits").asLong()).isEqualTo(2);
        assertThat(row.get("churn").get("outlier").asBoolean()).isTrue();
        assertThat(row.get("commentTotal").asInt()).isEqualTo(3);
        assertThat(json.toString()).doesNotContain(CANARY);
    }

    @Test
    void releaseListsOutliersFirstAndCapsAtFifty() {
        UUID id = UUID.randomUUID();
        when(releaseRef.resolve("Payments")).thenReturn(id);
        when(releaseService.get(id)).thenReturn(release(id,
                IntStream.range(0, 60).mapToObj(i -> item("PAY-" + (100 + i))).toList()));
        when(dashboardService.ticketChurn(id)).thenReturn(List.of(new TicketChurn("PAY-159", 1L, 0L, 0, false, true)));

        JsonNode json = mapper.valueToTree(tool.call(Map.of("release", "Payments")).result());

        assertThat(json.get("tickets")).hasSize(50);
        assertThat(json.get("tickets").get(0).get("key").asText()).isEqualTo("PAY-159");
        assertThat(json.get("truncated").asBoolean()).isTrue();
        assertThat(json.toString()).doesNotContain(CANARY);
    }

    @Test
    void badArgumentsAreRejected() {
        assertThatThrownBy(() -> tool.call(Map.of())).isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> tool.call(Map.of("key", "pay 1"))).isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> tool.call(Map.of("key", "X".repeat(41) + "-1"))).isInstanceOf(ToolArgumentException.class);
    }
}
