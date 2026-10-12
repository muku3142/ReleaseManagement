package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.PromotionRejectedException;
import com.evision.releasemgmt.domain.promotion.PromotionRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.promotion.StageNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromotionController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class PromotionControllerTest {

    private static final String ACTOR = "557058:abc-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromotionService service;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private final UUID releaseId = UUID.randomUUID();
    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final Stage test = new Stage(UUID.randomUUID(), "TEST", 2, true, null);

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject(ACTOR).claim("token_type", "access"));
    }

    private void stubActors() {
        when(actorResolution.resolve(any(String[].class)))
                .thenReturn(new ResolvedActors(Map.of(ACTOR, "rm@acme.com", "acc-2", "dev@acme.com")));
    }

    private Promotion entry(Stage stage, String by, String deployedAt, String recordedAt) {
        return new Promotion(UUID.randomUUID(), releaseId, stage.id(), PromotionOutcome.SUCCESS,
                Instant.parse(deployedAt), Instant.parse(recordedAt), by, null, null, null);
    }

    private PromotionHistory history(Promotion... entries) {
        return new PromotionHistory(releaseId, true, Instant.parse("2026-10-12T08:00:00Z"),
                List.of(dev, test), List.of(entries));
    }

    private String body(String stageId, String outcome, String extra) {
        return "{\"stageId\":" + (stageId == null ? "null" : "\"" + stageId + "\"")
                + ",\"outcome\":" + (outcome == null ? "null" : "\"" + outcome + "\"") + extra + "}";
    }

    private org.springframework.test.web.servlet.ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post("/api/releases/{id}/promotions", releaseId).with(csrf()).with(authenticated())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void getReturnsTheLadderTheDerivedStagesAndEntriesNewestFirst() throws Exception {
        Promotion older = entry(dev, ACTOR, "2026-10-12T09:00:00Z", "2026-10-12T09:00:01Z");
        Promotion newer = entry(test, "acc-2", "2026-10-12T09:30:00Z", "2026-10-12T09:30:01Z");
        when(service.history(releaseId)).thenReturn(history(older, newer));
        stubActors();

        mockMvc.perform(get("/api/releases/{id}/promotions", releaseId).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].name").value("DEV"))
                .andExpect(jsonPath("$.stages[1].production").value(true))
                .andExpect(jsonPath("$.stages[1].retired").value(false))
                .andExpect(jsonPath("$.currentStageId").value(test.id().toString()))
                .andExpect(jsonPath("$.nextStageId").doesNotExist())
                .andExpect(jsonPath("$.entries[0].id").value(newer.id().toString()))
                .andExpect(jsonPath("$.entries[0].stageName").value("TEST"))
                .andExpect(jsonPath("$.entries[0].recordedBy").value("dev@acme.com"))
                .andExpect(jsonPath("$.entries[0].effective").value(true))
                .andExpect(jsonPath("$.entries[1].recordedBy").value("rm@acme.com"));
        verify(actorResolution, times(1)).resolve(any(String[].class));
    }

    @Test
    void postRecordsWithThePrincipalAsActorAndIgnoresAnyBodyActor() throws Exception {
        when(service.record(eq(releaseId), any(), any())).thenReturn(history());
        stubActors();
        UUID deployedAtStage = dev.id();

        postBody(body(deployedAtStage.toString(), "SUCCESS",
                ",\"deployedAt\":\"2026-10-12T09:00:00Z\",\"note\":\"smoke ok\",\"recordedBy\":\"someone-else\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stages[0].name").value("DEV"));

        ArgumentCaptor<PromotionDraft> draft = ArgumentCaptor.forClass(PromotionDraft.class);
        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(service).record(eq(releaseId), draft.capture(), actor.capture());
        assertThat(actor.getValue()).isEqualTo(ACTOR);
        assertThat(draft.getValue()).isEqualTo(new PromotionDraft(dev.id(), PromotionOutcome.SUCCESS,
                Instant.parse("2026-10-12T09:00:00Z"), "smoke ok"));
    }

    @Test
    void postWithoutDeployedAtPassesNull() throws Exception {
        when(service.record(eq(releaseId), any(), any())).thenReturn(history());
        stubActors();

        postBody(body(dev.id().toString(), "FAILED", "")).andExpect(status().isCreated());

        ArgumentCaptor<PromotionDraft> draft = ArgumentCaptor.forClass(PromotionDraft.class);
        verify(service).record(eq(releaseId), draft.capture(), any());
        assertThat(draft.getValue().deployedAt()).isNull();
        assertThat(draft.getValue().note()).isNull();
    }

    @Test
    void anOverlongNoteIsRejected() throws Exception {
        postBody(body(dev.id().toString(), "SUCCESS", ",\"note\":\"" + "x".repeat(2001) + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void aMissingStageOrOutcomeIsRejected() throws Exception {
        postBody(body(null, "SUCCESS", "")).andExpect(status().isBadRequest());
        postBody(body(dev.id().toString(), null, "")).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownOutcomeIsRejected() throws Exception {
        postBody(body(dev.id().toString(), "STARTED", "")).andExpect(status().isBadRequest());
    }

    @Test
    void ruleRejectionsMapToStableReasonCodes() throws Exception {
        assertRejection(PromotionRejection.RELEASE_NOT_FROZEN, 409);
        assertRejection(PromotionRejection.PROMOTION_STAGE_SKIPPED, 409);
        assertRejection(PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID, 422);
        assertRejection(PromotionRejection.PROMOTION_OUTCOME_NOT_RECORDABLE, 422);
    }

    private void assertRejection(PromotionRejection reason, int httpStatus) throws Exception {
        when(service.record(eq(releaseId), any(), any())).thenThrow(new PromotionRejectedException(reason));

        postBody(body(dev.id().toString(), "SUCCESS", ""))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.reason").value(reason.name()));
    }

    @Test
    void notFoundExceptionsMapTo404() throws Exception {
        when(service.history(releaseId)).thenThrow(new ReleaseNotFoundException());
        mockMvc.perform(get("/api/releases/{id}/promotions", releaseId).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("RELEASE_NOT_FOUND"));

        when(service.record(eq(releaseId), any(), any())).thenThrow(new StageNotFoundException());
        postBody(body(dev.id().toString(), "SUCCESS", ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("STAGE_NOT_FOUND"));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        // A request carrying a bearer credential is exempt from CSRF by Spring's resource-server wiring
        // (see SecurityFilterChainIT), so the check only bites when no credential is resolvable.
        mockMvc.perform(post("/api/releases/{id}/promotions", releaseId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(dev.id().toString(), "SUCCESS", "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/releases/{id}/promotions", releaseId)).andExpect(status().isUnauthorized());
    }
}
