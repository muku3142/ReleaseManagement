package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.promotion.LadderEditRejectedException;
import com.evision.releasemgmt.domain.promotion.LadderEditRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.promotion.StageNotFoundException;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

@WebMvcTest(EnvironmentLadderController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class EnvironmentLadderControllerTest {

    private static final String BASE = "/api/environment-ladder";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnvironmentLadderService service;

    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final Stage test = new Stage(UUID.randomUUID(), "TEST", 2, false, Instant.parse("2026-10-12T08:00:00Z"));
    private final Stage prod = new Stage(UUID.randomUUID(), "PROD", 3, true, null);

    @BeforeEach
    void ladder() {
        when(service.ladder()).thenReturn(List.of(dev, test, prod));
        when(service.referencedStageIds()).thenReturn(Set.of(dev.id()));
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
    void getReturnsTheLadderInPositionOrderWithInUseFlags() throws Exception {
        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].name").value("DEV"))
                .andExpect(jsonPath("$.stages[0].inUse").value(true))
                .andExpect(jsonPath("$.stages[1].retired").value(true))
                .andExpect(jsonPath("$.stages[1].inUse").value(false))
                .andExpect(jsonPath("$.stages[2].production").value(true));
    }

    @Test
    void postStageCreatesAndReturnsTheLadder() throws Exception {
        send(post(BASE + "/stages"), "{\"name\":\"QA\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stages.length()").value(3));
        verify(service).add("QA");
    }

    @Test
    void blankAndOverlongNamesAreRejected() throws Exception {
        send(post(BASE + "/stages"), "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        send(post(BASE + "/stages"), "{\"name\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void putRenamesAStage() throws Exception {
        send(put(BASE + "/stages/{id}", dev.id()), "{\"name\":\"Staging\"}").andExpect(status().isOk());
        verify(service).rename(dev.id(), "Staging");
    }

    @Test
    void putOrderReordersAndBoundsThePayload() throws Exception {
        send(put(BASE + "/order"), "{\"stageIds\":[\"" + prod.id() + "\",\"" + dev.id() + "\"]}")
                .andExpect(status().isOk());
        verify(service).reorder(List.of(prod.id(), dev.id()));

        String tooMany = Stream.generate(() -> "\"" + UUID.randomUUID() + "\"").limit(51)
                .collect(java.util.stream.Collectors.joining(",", "{\"stageIds\":[", "]}"));
        send(put(BASE + "/order"), tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void retireRestoreProductionAndDeleteDelegate() throws Exception {
        send(post(BASE + "/stages/{id}/retire", dev.id())).andExpect(status().isOk());
        send(post(BASE + "/stages/{id}/restore", test.id())).andExpect(status().isOk());
        send(put(BASE + "/production-stage"), "{\"stageId\":\"" + dev.id() + "\"}").andExpect(status().isOk());
        send(delete(BASE + "/stages/{id}", test.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.stages.length()").value(3));

        verify(service).retire(dev.id());
        verify(service).restore(test.id());
        verify(service).moveProduction(dev.id());
        verify(service).delete(test.id());
    }

    @Test
    void rejectionsMapToStableStatusAndReason() throws Exception {
        doThrow(new LadderEditRejectedException(LadderEditRejection.STAGE_IN_USE)).when(service).delete(dev.id());
        send(delete(BASE + "/stages/{id}", dev.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("STAGE_IN_USE"));

        doThrow(new LadderEditRejectedException(LadderEditRejection.STAGE_ORDER_MISMATCH)).when(service)
                .reorder(List.of(dev.id()));
        send(put(BASE + "/order"), "{\"stageIds\":[\"" + dev.id() + "\"]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("STAGE_ORDER_MISMATCH"));

        doThrow(new StageNotFoundException()).when(service).retire(test.id());
        send(post(BASE + "/stages/{id}/retire", test.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("STAGE_NOT_FOUND"));
    }

    @Test
    void mutationWithoutCsrfIsForbidden() throws Exception {
        // No credential is sent: a bearer-carrying request is CSRF-exempt by Spring's resource-server wiring.
        mockMvc.perform(post(BASE + "/stages").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"QA\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/stages/{id}", dev.id())).andExpect(status().isForbidden());
    }

    @Test
    void getWithoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
}
