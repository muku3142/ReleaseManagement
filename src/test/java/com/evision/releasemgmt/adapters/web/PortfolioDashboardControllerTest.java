package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.PortfolioDashboardService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioDashboardController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class PortfolioDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioDashboardService service;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("acc-1").claim("token_type", "access"));
    }

    private static PortfolioDashboardService.ReleaseBrief brief() {
        return new PortfolioDashboardService.ReleaseBrief(UUID.randomUUID(), "Autumn", "2.0",
                Instant.parse("2026-10-02T00:00:00Z"));
    }

    @Test
    void summaryExposesTilesRowsAndTotals() throws Exception {
        PortfolioDashboardService.StatusCounts counts = new PortfolioDashboardService.StatusCounts(1, 2, 3);
        PortfolioDashboardService.ProductRow row = new PortfolioDashboardService.ProductRow(UUID.randomUUID(), "Alpha",
                false, counts, brief(), 4, 1);
        when(service.summary(Set.of(), false)).thenReturn(new PortfolioDashboardService.Summary(
                new PortfolioDashboardService.Tiles(5, 6, 7, 8, 9), List.of(row), counts, null));

        mockMvc.perform(get("/api/dashboard/portfolio/summary").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiles.clientsBehind").value(5))
                .andExpect(jsonPath("$.tiles.installsLast30Days").value(9))
                .andExpect(jsonPath("$.products[0].releases.open").value(1))
                .andExpect(jsonPath("$.products[0].newestFrozen.name").value("Autumn"))
                .andExpect(jsonPath("$.products[0].clientsOnNewest").value(1))
                .andExpect(jsonPath("$.totals.inProduction").value(3))
                .andExpect(jsonPath("$.unassigned").doesNotExist());
    }

    @Test
    void behindPassesTheScopeAndExposesBothGroups() throws Exception {
        UUID a = UUID.randomUUID();
        PortfolioDashboardService.RunBrief run = new PortfolioDashboardService.RunBrief(UUID.randomUUID(), "Old", "1.0",
                Instant.parse("2026-09-01T00:00:00Z"), true);
        PortfolioDashboardService.BehindRow behind = new PortfolioDashboardService.BehindRow(UUID.randomUUID(), "C1",
                false, a, "Alpha", run, 2, brief());
        PortfolioDashboardService.NotInstalledRow none = new PortfolioDashboardService.NotInstalledRow(UUID.randomUUID(),
                "C2", false, a, "Alpha", null);
        when(service.behind(Set.of(a), true)).thenReturn(new PortfolioDashboardService.Behind(List.of(behind), List.of(none)));

        mockMvc.perform(get("/api/dashboard/portfolio/behind?productIds=" + a + "&includeArchived=true").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.behind[0].releasesBehind").value(2))
                .andExpect(jsonPath("$.behind[0].runs.inProduction").value(true))
                .andExpect(jsonPath("$.notInstalled[0].newest").doesNotExist());
    }

    @Test
    void matrixCellOfANotInstalledPairHasNoRun() throws Exception {
        UUID a = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(service.matrix(Set.of(), false)).thenReturn(new PortfolioDashboardService.Matrix(
                List.of(new PortfolioDashboardService.Column(a, "Alpha", false)),
                List.of(new PortfolioDashboardService.Column(c, "C1", false)),
                List.of(new PortfolioDashboardService.Cell(c, a, null, null, brief(), false))));

        mockMvc.perform(get("/api/dashboard/portfolio/matrix").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("Alpha"))
                .andExpect(jsonPath("$.cells[0].run").doesNotExist())
                .andExpect(jsonPath("$.cells[0].upToDate").value(false));
    }

    @Test
    void moreThanFiftyProductIdsIsRejected() throws Exception {
        String ids = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID().toString()).collect(Collectors.joining(","));

        mockMvc.perform(get("/api/dashboard/portfolio/summary?productIds=" + ids).with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void aNonBooleanIncludeArchivedIsRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/portfolio/matrix?includeArchived=maybe").with(authenticated()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnauthenticatedRequestIsRefusedWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/dashboard/portfolio/summary")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
