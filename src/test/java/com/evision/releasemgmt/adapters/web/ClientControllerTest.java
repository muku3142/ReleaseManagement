package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ClientDetail;
import com.evision.releasemgmt.application.ClientService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.ClientNotFoundException;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class ClientControllerTest {

    private static final String BASE = "/api/clients";
    private static final Instant T1 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-02T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService service;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private final Product alpha = new Product(UUID.randomUUID(), "Alpha", null);
    private final Product beta = new Product(UUID.randomUUID(), "Beta", Instant.parse("2026-01-01T00:00:00Z"));
    private final Client acme = new Client(UUID.randomUUID(), "Acme", null, Set.of(alpha.id(), beta.id()));
    private final Client zed = new Client(UUID.randomUUID(), "zed", null, Set.of());
    private final Client dormant = new Client(UUID.randomUUID(), "Aaa", T1, Set.of());
    private final UUID releaseId = UUID.randomUUID();
    private final UUID originalId = UUID.randomUUID();
    private final UUID correctionId = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        when(service.list()).thenReturn(List.of(dormant, zed, acme));
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of("acc-1", "rm@example.com")));
        ClientDeployment original = new ClientDeployment(originalId, releaseId, alpha.id(), acme.id(),
                ClientDeploymentOutcome.SUCCESS, T1, T1, "acc-1", "first", null, null);
        ClientDeployment correction = new ClientDeployment(correctionId, releaseId, alpha.id(), acme.id(),
                ClientDeploymentOutcome.FAILED, T2, T2, "acc-1", null, originalId, "wrong outcome");
        ClientDetail detail = new ClientDetail(acme,
                List.of(new ClientDetail.Run(alpha, true,
                                new CurrentRun(alpha.id(), releaseId, "R1", "1.0", T2), false),
                        new ClientDetail.Run(beta, false, null, false)),
                List.of(new ClientDetail.HistoryRow(new ClientInstall(original, "R1", "1.0"), "Alpha", false,
                                correctionId),
                        new ClientDetail.HistoryRow(new ClientInstall(correction, "R1", "1.0"), "Alpha", false, null)));
        when(service.detail(acme.id())).thenReturn(detail);
    }

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(authenticated())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(authenticated()));
    }

    @Test
    void listIsOrderedByNameWithArchivedLastAndCarriesProductIds() throws Exception {
        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].name").value("Acme"))
                .andExpect(jsonPath("$.clients[0].productIds.length()").value(2))
                .andExpect(jsonPath("$.clients[1].name").value("zed"))
                .andExpect(jsonPath("$.clients[2].name").value("Aaa"))
                .andExpect(jsonPath("$.clients[2].archived").value(true));
    }

    @Test
    void detailCarriesRunsAndHistoryPerTheContract() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", acme.id()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.runs[0].productName").value("Alpha"))
                .andExpect(jsonPath("$.runs[0].hasInstalls").value(true))
                .andExpect(jsonPath("$.runs[0].current.releaseName").value("R1"))
                .andExpect(jsonPath("$.runs[0].current.inProduction").value(false))
                .andExpect(jsonPath("$.runs[1].productArchived").value(true))
                .andExpect(jsonPath("$.runs[1].current").doesNotExist())
                .andExpect(jsonPath("$.history[0].id").value(correctionId.toString()))
                .andExpect(jsonPath("$.history[0].effective").value(true))
                .andExpect(jsonPath("$.history[0].recordedBy").value("rm@example.com"))
                .andExpect(jsonPath("$.history[0].supersedesId").value(originalId.toString()))
                .andExpect(jsonPath("$.history[1].id").value(originalId.toString()))
                .andExpect(jsonPath("$.history[1].effective").value(false))
                .andExpect(jsonPath("$.history[1].supersededById").value(correctionId.toString()));
    }

    @Test
    void postRenameArchiveAndUnarchiveDelegate() throws Exception {
        send(post(BASE), "{\"name\":\"New\"}").andExpect(status().isCreated());
        send(put(BASE + "/{id}", acme.id()), "{\"name\":\"Acme 2\"}").andExpect(status().isOk());
        send(post(BASE + "/{id}/archive", acme.id())).andExpect(status().isOk());
        send(post(BASE + "/{id}/unarchive", dormant.id())).andExpect(status().isOk());

        verify(service).add("New");
        verify(service).rename(acme.id(), "Acme 2");
        verify(service).archive(acme.id());
        verify(service).unarchive(dormant.id());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        send(post(BASE), "{\"name\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void linkAndUnlinkReturnTheClientDetail() throws Exception {
        send(put(BASE + "/{c}/products/{p}", acme.id(), alpha.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(acme.id().toString()));
        send(delete(BASE + "/{c}/products/{p}", acme.id(), alpha.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runs.length()").value(2));

        verify(service).link(acme.id(), alpha.id());
        verify(service).unlink(acme.id(), alpha.id());
    }

    @Test
    void unlinkWithInstallsIsAConflict() throws Exception {
        doThrow(new PortfolioEditRejectedException(PortfolioEditRejection.CLIENT_LINK_HAS_INSTALLS))
                .when(service).unlink(acme.id(), alpha.id());

        send(delete(BASE + "/{c}/products/{p}", acme.id(), alpha.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("CLIENT_LINK_HAS_INSTALLS"));
    }

    @Test
    void linkingAnArchivedProductIsAConflict() throws Exception {
        doThrow(new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED))
                .when(service).link(acme.id(), beta.id());

        send(put(BASE + "/{c}/products/{p}", acme.id(), beta.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PRODUCT_ARCHIVED"));
    }

    @Test
    void unknownClientIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(service.detail(unknown)).thenThrow(new ClientNotFoundException());

        mockMvc.perform(get(BASE + "/{id}", unknown).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("CLIENT_NOT_FOUND"));
    }

    @Test
    void aNonUuidPathIsABadRequest() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid").with(authenticated())).andExpect(status().isBadRequest());
    }

    @Test
    void thereIsNoRouteToDeleteAClient() throws Exception {
        send(delete(BASE + "/{id}", acme.id())).andExpect(status().isMethodNotAllowed());
    }
}
