package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.DashboardService.DashboardView;
import com.evision.releasemgmt.application.DashboardService.ReleaseMetrics;
import com.evision.releasemgmt.application.DashboardService.Summary;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
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

class DashboardToolTest {

    private final DashboardService dashboardService = mock(DashboardService.class);
    private final EnvironmentLadderService ladderService = mock(EnvironmentLadderService.class);
    private final DashboardTool tool = new DashboardTool(dashboardService, ladderService);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static ReleaseMetrics row(int i, UUID stageId) {
        return new ReleaseMetrics(new DashboardRelease(UUID.randomUUID(), "R" + i, "1." + i, ReleaseStatus.OPEN,
                Instant.EPOCH, null, 0), stageId, null, null, null, null, null, null);
    }

    @Test
    void summaryKeepsNullMediansAndStageCounts() {
        Stage qa = new Stage(UUID.randomUUID(), "QA", 1, false, null);
        Summary summary = new Summary(30, 40, null, 0, null, 0, Map.of(qa.id(), 30L), 0);
        when(dashboardService.releases(false)).thenReturn(new DashboardView(List.of(qa), summary,
                IntStream.range(0, 30).mapToObj(i -> row(i, qa.id())).toList()));
        when(ladderService.ladder()).thenReturn(List.of(qa));

        JsonNode json = mapper.valueToTree(tool.call(Map.of()).result());

        assertThat(json.get("summary").get("medianCreatedToFrozenSeconds").isNull()).isTrue();
        assertThat(json.get("summary").get("createdToFrozenCount").asInt()).isZero();
        assertThat(json.get("stages").get(0).get("releaseCount").asLong()).isEqualTo(30);
        assertThat(json.get("releases")).hasSize(25);
        assertThat(json.get("releases").get(0).get("currentStage").asText()).isEqualTo("QA");
        assertThat(json.get("truncated").asBoolean()).isTrue();
    }

    @Test
    void scopeAllIsPassedThroughAndOtherValuesRejected() {
        Summary summary = new Summary(0, 0, null, 0, null, 0, Map.of(), 0);
        when(dashboardService.releases(true)).thenReturn(new DashboardView(List.of(), summary, List.of()));
        assertThat(tool.call(Map.of("scope", "all")).sourceId()).isEqualTo("dashboard:all");
        assertThatThrownBy(() -> tool.call(Map.of("scope", "everything"))).isInstanceOf(ToolArgumentException.class);
    }
}
