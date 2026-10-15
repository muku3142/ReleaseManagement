package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.release.ManualItem;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleasesToolTest {

    private static final String CANARY = "CANARY-TEXT";

    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final PromotionService promotionService = mock(PromotionService.class);
    private final ReleasesTool tool = new ReleasesTool(releaseService, promotionService, new ReleaseRef(releaseService));
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static ReleaseSummary summary(String name, String version) {
        return new ReleaseSummary(UUID.randomUUID(), name, version, ReleaseStatus.OPEN, "PAY", Instant.EPOCH, "a",
                null, null, null, null, 2);
    }

    private static Release release(UUID id) {
        TicketDetail ticket = new TicketDetail("PAY-1", CANARY, "Bug", "Done", null, null, null, List.of(), null,
                CANARY, List.of(CANARY), 1, "1", Instant.EPOCH, StatusCategory.DONE, null, false);
        ManualItem manual = new ManualItem(UUID.randomUUID(), "manual", CANARY, null, Instant.EPOCH);
        return new Release(id, "Payments", "2.4", ReleaseStatus.OPEN, "PAY", Instant.EPOCH, "a", null, null, null, null,
                List.of(new ReleaseItem(ticket, Instant.EPOCH, CodeLinks.notAttempted())), List.of(manual));
    }

    @Test
    void listIsCappedAndFlaggedTruncated() {
        when(releaseService.search(anyString())).thenReturn(
                IntStream.range(0, 30).mapToObj(i -> summary("R" + i, "1." + i)).toList());
        JsonNode json = mapper.valueToTree(tool.call(Map.of()).result());
        assertThat(json.get("releases")).hasSize(25);
        assertThat(json.get("truncated").asBoolean()).isTrue();
    }

    @Test
    void detailCarriesStructuredFieldsOnlyAndKeepsNulls() {
        UUID id = UUID.randomUUID();
        when(releaseService.get(id)).thenReturn(release(id));
        when(promotionService.history(id)).thenReturn(new PromotionHistory(id, false, null, List.of(), List.of()));
        JsonNode json = mapper.valueToTree(tool.call(Map.of("release", id.toString())).result());
        assertThat(json.get("ticketCount").asInt()).isEqualTo(1);
        assertThat(json.get("manualItemCount").asInt()).isEqualTo(1);
        assertThat(json.get("ticketsByStatusCategory").get("DONE").asLong()).isEqualTo(1);
        assertThat(json.get("currentStage").isNull()).isTrue();
        assertThat(json.get("shippedAt").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain(CANARY);
    }

    @Test
    void unknownReleaseIsAnArgumentError() {
        when(releaseService.search(any())).thenReturn(List.of());
        assertThatThrownBy(() -> tool.call(Map.of("release", "nope"))).isInstanceOf(ToolArgumentException.class);
    }

    @Test
    void parametersAreAFlatSchema() {
        assertThat(tool.description().length()).isLessThanOrEqualTo(300);
        assertThat(tool.parameters()).containsEntry("additionalProperties", false);
    }
}
