package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ClientDeploymentService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentNotFoundException;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentRejectedException;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentRejection;
import com.evision.releasemgmt.domain.portfolio.RejectedClient;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientDeploymentController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class ClientDeploymentControllerTest {

    private static final String ACTOR = "557058:abc-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientDeploymentService service;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private final UUID releaseId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final Client acme = new Client(UUID.randomUUID(), "Acme", null, Set.of(productId));
    private final Client globex = new Client(UUID.randomUUID(), "Globex", null, Set.of(productId));

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject(ACTOR).claim("token_type", "access"));
    }

    private void stubActors() {
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of(ACTOR, "rm@acme.com")));
    }

    private ClientDeploymentHistory history(ClientDeployment... entries) {
        return new ClientDeploymentHistory(releaseId, true,
                List.of(new ReleaseProduct(productId, "Core", false, true)), List.of(acme, globex), List.of(entries));
    }

    private ClientDeployment entry(Client client, String installedAt, UUID supersedes) {
        return new ClientDeployment(UUID.randomUUID(), releaseId, productId, client.id(),
                ClientDeploymentOutcome.SUCCESS, Instant.parse(installedAt), Instant.parse(installedAt).plusSeconds(1),
                ACTOR, null, supersedes, supersedes == null ? null : "typo");
    }

    private String body(String clientIds, String extra) {
        return "{\"productId\":\"" + productId + "\",\"clientIds\":" + clientIds + ",\"outcome\":\"SUCCESS\"" + extra + "}";
    }

    private ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post("/api/releases/{id}/installs", releaseId).with(csrf()).with(authenticated())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions postCorrection(UUID installId, String json) throws Exception {
        return mockMvc.perform(post("/api/releases/{id}/installs/{i}/corrections", releaseId, installId)
                .with(csrf()).with(authenticated()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void getReturnsTheContractShapeNewestFirst() throws Exception {
        ClientDeployment older = entry(acme, "2026-10-12T09:00:00Z", null);
        ClientDeployment newer = entry(globex, "2026-10-12T09:30:00Z", null);
        when(service.history(releaseId)).thenReturn(history(older, newer));
        when(service.reachedProduction(releaseId)).thenReturn(false);
        stubActors();

        mockMvc.perform(get("/api/releases/{id}/installs", releaseId).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseId").value(releaseId.toString()))
                .andExpect(jsonPath("$.frozen").value(true))
                .andExpect(jsonPath("$.inProduction").value(false))
                .andExpect(jsonPath("$.products[0].name").value("Core"))
                .andExpect(jsonPath("$.products[0].archived").value(false))
                .andExpect(jsonPath("$.entries[0].id").value(newer.id().toString()))
                .andExpect(jsonPath("$.entries[0].clientName").value("Globex"))
                .andExpect(jsonPath("$.entries[0].productName").value("Core"))
                .andExpect(jsonPath("$.entries[0].recordedBy").value("rm@acme.com"))
                .andExpect(jsonPath("$.entries[0].effective").value(true))
                .andExpect(jsonPath("$.entries[1].clientName").value("Acme"));
    }

    @Test
    void postRecordsWithThePrincipalAsActorAndIgnoresAnyBodyActor() throws Exception {
        when(service.record(eq(releaseId), any(), any(), any())).thenReturn(history());
        stubActors();

        postBody(body("[\"" + acme.id() + "\",\"" + globex.id() + "\"]",
                ",\"installedAt\":\"2026-10-12T09:00:00Z\",\"note\":\"ok\",\"recordedBy\":\"evil\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.products[0].name").value("Core"));

        ArgumentCaptor<ClientDeploymentDraft> draft = ArgumentCaptor.forClass(ClientDeploymentDraft.class);
        ArgumentCaptor<List<UUID>> clientIds = ArgumentCaptor.captor();
        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(service).record(eq(releaseId), draft.capture(), clientIds.capture(), actor.capture());
        assertThat(actor.getValue()).isEqualTo(ACTOR);
        assertThat(clientIds.getValue()).containsExactly(acme.id(), globex.id());
        assertThat(draft.getValue()).isEqualTo(new ClientDeploymentDraft(productId, ClientDeploymentOutcome.SUCCESS,
                Instant.parse("2026-10-12T09:00:00Z"), "ok"));
    }

    @Test
    void emptyOrMalformedClientIdsAreRejected() throws Exception {
        postBody(body("[]", "")).andExpect(status().isBadRequest());
        postBody(body("[\"not-a-uuid\"]", "")).andExpect(status().isBadRequest());
        postBody(body("[null]", "")).andExpect(status().isBadRequest());
    }

    @Test
    void moreThan200ClientsAreRejected() throws Exception {
        String ids = "[" + String.join(",", java.util.Collections.nCopies(201, "\"" + UUID.randomUUID() + "\"")) + "]";
        postBody(body(ids, "")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectedClientsMapTo409NamingEachClient() throws Exception {
        when(service.record(eq(releaseId), any(), any(), any())).thenThrow(new ClientDeploymentRejectedException(
                ClientDeploymentRejection.INSTALL_CLIENTS_REJECTED,
                List.of(new RejectedClient(acme.id(), "Acme", RejectedClient.Reason.NOT_LINKED),
                        new RejectedClient(globex.id(), "Globex", RejectedClient.Reason.ARCHIVED))));

        postBody(body("[\"" + acme.id() + "\"]", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INSTALL_CLIENTS_REJECTED"))
                .andExpect(jsonPath("$.clients[0].reason").value("NOT_LINKED"))
                .andExpect(jsonPath("$.clients[1].reason").value("ARCHIVED"))
                .andExpect(jsonPath("$.clients[1].name").value("Globex"));
    }

    @Test
    void ruleRejectionsMapToStableReasonCodes() throws Exception {
        assertRejection(ClientDeploymentRejection.INSTALL_RELEASE_NOT_FROZEN, 409);
        assertRejection(ClientDeploymentRejection.INSTALL_INSTALLED_AT_INVALID, 422);
    }

    private void assertRejection(ClientDeploymentRejection reason, int httpStatus) throws Exception {
        when(service.record(eq(releaseId), any(), any(), any())).thenThrow(new ClientDeploymentRejectedException(reason));

        postBody(body("[\"" + acme.id() + "\"]", ""))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.reason").value(reason.name()));
    }

    @Test
    void correctionPostsWithThePrincipalAsActor() throws Exception {
        UUID installId = UUID.randomUUID();
        when(service.correct(eq(releaseId), eq(installId), any(), any(), any(), any(), any())).thenReturn(history());
        stubActors();

        postCorrection(installId, "{\"outcome\":\"VOIDED\",\"installedAt\":\"2026-10-12T09:00:00Z\","
                + "\"reason\":\"wrong client\",\"recordedBy\":\"evil\"}")
                .andExpect(status().isCreated());

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(service).correct(eq(releaseId), eq(installId), eq(ClientDeploymentOutcome.VOIDED),
                eq(Instant.parse("2026-10-12T09:00:00Z")), eq(null), eq("wrong client"), actor.capture());
        assertThat(actor.getValue()).isEqualTo(ACTOR);
    }

    @Test
    void correctionRequiresAReasonAndAnInstalledTime() throws Exception {
        UUID installId = UUID.randomUUID();
        postCorrection(installId, "{\"outcome\":\"VOIDED\",\"installedAt\":\"2026-10-12T09:00:00Z\"}")
                .andExpect(status().isBadRequest());
        postCorrection(installId, "{\"outcome\":\"VOIDED\",\"installedAt\":\"2026-10-12T09:00:00Z\",\"reason\":\"  \"}")
                .andExpect(status().isBadRequest());
        postCorrection(installId, "{\"outcome\":\"VOIDED\",\"reason\":\"typo\"}").andExpect(status().isBadRequest());
    }

    @Test
    void correctingAnUnknownInstallIs404() throws Exception {
        UUID installId = UUID.randomUUID();
        when(service.correct(eq(releaseId), eq(installId), any(), any(), any(), any(), any()))
                .thenThrow(new ClientDeploymentNotFoundException());

        postCorrection(installId, "{\"outcome\":\"VOIDED\",\"installedAt\":\"2026-10-12T09:00:00Z\",\"reason\":\"typo\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("INSTALL_NOT_FOUND"));
    }

    @Test
    void getWithoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/releases/{id}/installs", releaseId)).andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/releases/{id}/installs", releaseId)
                        .contentType(MediaType.APPLICATION_JSON).content(body("[\"" + acme.id() + "\"]", "")))
                .andExpect(status().isForbidden());
    }
}
