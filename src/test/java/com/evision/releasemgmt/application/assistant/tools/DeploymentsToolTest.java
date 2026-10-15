package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.DashboardService.DeploymentPage;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentsToolTest {

    private static final String CANARY = "CANARY-TEXT";
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    private final PromotionService promotionService = mock(PromotionService.class);
    private final DashboardService dashboardService = mock(DashboardService.class);
    private final EnvironmentLadderService ladderService = mock(EnvironmentLadderService.class);
    private final ActorResolutionService actors = mock(ActorResolutionService.class);
    private final ReleaseRef releaseRef = mock(ReleaseRef.class);
    private final DeploymentsTool tool = new DeploymentsTool(promotionService, dashboardService, ladderService, actors,
            releaseRef, Clock.fixed(NOW, ZoneOffset.UTC));
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final Stage qa = new Stage(UUID.randomUUID(), "QA", 1, false, null);

    @Test
    void releaseHistoryNeverCarriesNoteOrCorrectionReason() {
        UUID releaseId = UUID.randomUUID();
        Promotion original = new Promotion(UUID.randomUUID(), releaseId, qa.id(), PromotionOutcome.FAILED,
                Instant.EPOCH, Instant.EPOCH, "acc-1", CANARY, null, null);
        Promotion correction = new Promotion(UUID.randomUUID(), releaseId, qa.id(), PromotionOutcome.SUCCESS,
                Instant.EPOCH.plusSeconds(5), Instant.EPOCH.plusSeconds(6), "acc-1", CANARY, original.id(), CANARY);
        when(releaseRef.resolve("R")).thenReturn(releaseId);
        when(promotionService.history(releaseId)).thenReturn(
                new PromotionHistory(releaseId, true, Instant.EPOCH, List.of(qa), List.of(original, correction)));
        when(actors.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of("acc-1", "a@x.com")));

        JsonNode json = mapper.valueToTree(tool.call(Map.of("release", "R")).result());

        assertThat(json.toString()).doesNotContain(CANARY);
        assertThat(json.get("currentStage").asText()).isEqualTo("QA");
        JsonNode first = json.get("entries").get(0);
        assertThat(first.get("superseded").asBoolean()).isTrue();
        assertThat(first.get("recordedBy").asText()).isEqualTo("a@x.com");
        assertThat(json.get("entries").get(1).get("corrected").asBoolean()).isTrue();
    }

    @Test
    void recentUsesStageDaysAndLimitAndNeverReadsNote() {
        when(ladderService.ladder()).thenReturn(List.of(qa));
        DeploymentRow row = new DeploymentRow(UUID.randomUUID(), UUID.randomUUID(), "Payments", "2.4", qa.id(), "QA",
                false, PromotionOutcome.SUCCESS, NOW, "acc-1", CANARY, false);
        when(dashboardService.deployments(eq(qa.id()), any(), any(), eq(25), eq(0)))
                .thenReturn(new DeploymentPage(List.of(row), true));
        when(actors.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of()));

        JsonNode json = mapper.valueToTree(tool.call(Map.of("stage", "qa", "days", 7)).result());

        verify(dashboardService).deployments(qa.id(), NOW.minus(Duration.ofDays(7)), NOW, 25, 0);
        assertThat(json.toString()).doesNotContain(CANARY);
        assertThat(json.get("hasMore").asBoolean()).isTrue();
        assertThat(json.get("entries").get(0).get("recordedBy").asText()).isEqualTo("acc-1");
    }

    @Test
    void invalidStageAndDaysAreRejected() {
        when(ladderService.ladder()).thenReturn(List.of(qa));
        assertThatThrownBy(() -> tool.call(Map.of("stage", "Prod"))).isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("QA");
        assertThatThrownBy(() -> tool.call(Map.of("days", 0))).isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> tool.call(Map.of("days", 367))).isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> tool.call(Map.of("days", "ten"))).isInstanceOf(ToolArgumentException.class);
    }
}
