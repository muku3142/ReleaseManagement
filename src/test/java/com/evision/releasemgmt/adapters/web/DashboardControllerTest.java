package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.dashboard.ReleaseChurn;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class DashboardControllerTest {

    private static final String FROM = "2026-09-12T22:00:00Z";
    private static final String TO = "2026-10-12T22:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService service;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final Stage prod = new Stage(UUID.randomUUID(), "PROD", 2, true, null);

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("acc-1").claim("token_type", "access"));
    }

    private ResultActions getDeployments(String query) throws Exception {
        return mockMvc.perform(get("/api/dashboard/deployments?" + query).with(authenticated()));
    }

    private void assertValidationFailed(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    private DashboardService.DashboardView view() {
        DashboardRelease release = new DashboardRelease(UUID.randomUUID(), "Autumn", "2.0", ReleaseStatus.FROZEN,
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"), 2);
        Map<UUID, Long> perStage = new LinkedHashMap<>();
        perStage.put(dev.id(), 1L);
        DashboardService.Summary summary = new DashboardService.Summary(1, 3, 86400.0, 1, null, 0, perStage, 0);
        DashboardService.ReleaseMetrics metrics = new DashboardService.ReleaseMetrics(release, dev.id(), null, prod.id(),
                86400L, null, ReleaseChurn.of(List.of()));
        return new DashboardService.DashboardView(List.of(dev, prod), summary, List.of(metrics));
    }

    private DeploymentRow row(String by) {
        return new DeploymentRow(UUID.randomUUID(), UUID.randomUUID(), "Autumn", "2.0", dev.id(), "DEV", false,
                PromotionOutcome.SUCCESS, Instant.parse("2026-10-05T10:00:00Z"), by, "ok", false);
    }

    @Test
    void releasesDefaultsToActiveScopeAndExposesTheContract() throws Exception {
        when(service.releases(false)).thenReturn(view());

        mockMvc.perform(get("/api/dashboard/releases").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.visibleReleases").value(1))
                .andExpect(jsonPath("$.summary.totalReleases").value(3))
                .andExpect(jsonPath("$.summary.medianCreatedToFrozenSeconds").value(86400.0))
                .andExpect(jsonPath("$.summary.medianFrozenToProductionSeconds").doesNotExist())
                .andExpect(jsonPath("$.summary.releasesPerStage[0].stageId").value(dev.id().toString()))
                .andExpect(jsonPath("$.summary.releasesPerStage[0].count").value(1))
                .andExpect(jsonPath("$.summary.notPromoted").value(0))
                .andExpect(jsonPath("$.stages[1].production").value(true))
                .andExpect(jsonPath("$.stages[1].retired").value(false))
                .andExpect(jsonPath("$.releases[0].name").value("Autumn"))
                .andExpect(jsonPath("$.releases[0].status").value("FROZEN"))
                .andExpect(jsonPath("$.releases[0].currentStageId").value(dev.id().toString()))
                .andExpect(jsonPath("$.releases[0].lastFailedStageId").value(prod.id().toString()))
                .andExpect(jsonPath("$.releases[0].createdToFrozenSeconds").value(86400))
                .andExpect(jsonPath("$.releases[0].composition.manual").value(2))
                .andExpect(jsonPath("$.releases[0].composition.tickets").value(0))
                .andExpect(jsonPath("$.releases[0].churn.outliers").value(0))
                .andExpect(jsonPath("$.releases[0].churn.keyTagged").value(0));
    }

    @Test
    void scopeAllRequestsEveryRelease() throws Exception {
        when(service.releases(true)).thenReturn(view());

        mockMvc.perform(get("/api/dashboard/releases?scope=all").with(authenticated())).andExpect(status().isOk());

        verify(service).releases(true);
    }

    @Test
    void unknownScopeIsRejected() throws Exception {
        assertValidationFailed(mockMvc.perform(get("/api/dashboard/releases?scope=bogus").with(authenticated())));
    }

    @Test
    void deploymentsDefaultsToAllStagesFirstPageAndResolvesActorsOnce() throws Exception {
        when(service.deployments(null, Instant.parse(FROM), Instant.parse(TO), 50, 0))
                .thenReturn(new DashboardService.DeploymentPage(List.of(row("acc-1"), row("acc-2"), row("acc-1")), true));
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("acc-1", "rm@acme.com", "acc-2", "dev@acme.com")));

        getDeployments("from=" + FROM + "&to=" + TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].recordedBy").value("rm@acme.com"))
                .andExpect(jsonPath("$.entries[1].recordedBy").value("dev@acme.com"))
                .andExpect(jsonPath("$.entries[0].stageName").value("DEV"))
                .andExpect(jsonPath("$.entries[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.entries[0].corrected").value(false));
        ArgumentCaptor<String[]> ids = ArgumentCaptor.forClass(String[].class);
        verify(actorResolution, times(1)).resolve(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder("acc-1", "acc-2");
    }

    @Test
    void deploymentsPassesTheStageFilterAndPaging() throws Exception {
        when(service.deployments(eq(dev.id()), any(), any(), eq(20), eq(40)))
                .thenReturn(new DashboardService.DeploymentPage(List.of(), false));
        when(actorResolution.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of()));

        getDeployments("stageId=" + dev.id() + "&from=" + FROM + "&to=" + TO + "&limit=20&offset=40")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void malformedParametersAreRejected() throws Exception {
        assertValidationFailed(getDeployments("stageId=not-a-uuid&from=" + FROM + "&to=" + TO));
        assertValidationFailed(getDeployments("from=garbage&to=" + TO));
    }

    @Test
    void emptyOrInvertedRangeIsRejected() throws Exception {
        assertValidationFailed(getDeployments("from=" + FROM + "&to=" + FROM));
        assertValidationFailed(getDeployments("from=" + TO + "&to=" + FROM));
    }

    @Test
    void rangeLongerThanTheLimitPlusDstSlackIsRejected() throws Exception {
        assertValidationFailed(getDeployments("from=2025-01-01T00:00:00Z&to=2026-01-03T02:00:00Z"));
    }

    @Test
    void rangeOf366DaysPlusOneHourIsAccepted() throws Exception {
        when(service.deployments(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new DashboardService.DeploymentPage(List.of(), false));
        when(actorResolution.resolve(any(String[].class))).thenReturn(new ResolvedActors(Map.of()));

        getDeployments("from=2025-01-01T00:00:00Z&to=2026-01-02T01:00:00Z").andExpect(status().isOk());
    }

    @Test
    void outOfRangeLimitAndOffsetAreRejected() throws Exception {
        assertValidationFailed(getDeployments("from=" + FROM + "&to=" + TO + "&limit=0"));
        assertValidationFailed(getDeployments("from=" + FROM + "&to=" + TO + "&limit=101"));
        assertValidationFailed(getDeployments("from=" + FROM + "&to=" + TO + "&offset=-1"));
        verify(service, never()).deployments(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void requestsWithoutAJwtAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/releases")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/deployments?from=" + FROM + "&to=" + TO)).andExpect(status().isUnauthorized());
    }
}
