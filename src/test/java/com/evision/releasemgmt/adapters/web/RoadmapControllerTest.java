package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ProductService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.application.RoadmapImportService;
import com.evision.releasemgmt.application.RoadmapService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapFilter;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoadmapController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class, RoadmapControllerTest.FixedClock.class})
class RoadmapControllerTest {

    private static final String BASE = "/api/roadmap";
    private static final String ACTOR = "557058:abc-123";
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoadmapService service;

    @MockitoBean
    private RoadmapImportService importService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ActorResolutionService actorResolution;

    private final Product product = new Product(UUID.randomUUID(), "Core", null);

    @BeforeEach
    void products() {
        when(productService.list()).thenReturn(List.of(product));
    }

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject(ACTOR).claim("token_type", "access"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(authenticated())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private RoadmapItem item(String title, RiceInputs inputs) {
        RoadmapItem manual = RoadmapItem.manual(title, product.id(), ACTOR, NOW);
        return new RoadmapItem(manual.id(), manual.source(), null, null, title, null, null, null, null,
                manual.productId(), manual.status(), null, null, null, inputs, NOW, ACTOR, null);
    }

    private static RiceInputs scored() {
        return new RiceInputs(5, new BigDecimal("1"), 80, new BigDecimal("0.32"));
    }

    @Test
    void listCarriesRoundedScoreRankAndProduct() throws Exception {
        when(service.ranked(any())).thenReturn(List.of(
                new RankedItem(item("A", scored()), new BigDecimal("12.46"), 1),
                new RankedItem(item("B", RiceInputs.NONE), null, null)));

        mockMvc.perform(get(BASE + "/items").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].scoreDisplay").value("12.5"))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].product.name").value("Core"))
                .andExpect(jsonPath("$.items[0].impact").value(1))
                .andExpect(jsonPath("$.items[1].scoreDisplay").doesNotExist())
                .andExpect(jsonPath("$.items[1].missing.length()").value(4));
    }

    @Test
    void filterParametersReachTheService() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(service.ranked(any())).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/items").param("productIds", a + "," + b).param("done", "true")
                .with(authenticated())).andExpect(status().isOk());

        verify(service).ranked(new RoadmapFilter(Set.of(a, b), true, false));
    }

    @Test
    void invalidReachIsRejectedWithItsReason() throws Exception {
        send(put(BASE + "/items/" + UUID.randomUUID() + "/inputs"), "{\"reach\":2.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_REACH_INVALID"));
    }

    @Test
    void invalidImpactIsRejectedWithItsReason() throws Exception {
        send(put(BASE + "/items/" + UUID.randomUUID() + "/inputs"), "{\"impact\":0.75}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_IMPACT_INVALID"));
    }

    @Test
    void validInputsPassTheActorAndReturnTheItem() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setInputs(eq(id), any(), eq(ACTOR))).thenReturn(item("A", scored()));

        send(put(BASE + "/items/" + id + "/inputs"),
                "{\"reach\":5,\"impact\":1,\"confidence\":80,\"effort\":0.32}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.scoreDisplay").value("12.5"));

        verify(service).setInputs(eq(id), eq(scored()), eq(ACTOR));
    }

    @Test
    void lowercaseJiraKeyFailsValidation() throws Exception {
        send(post(BASE + "/items/jira"), "{\"keys\":[\"cnv-1\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void serviceRejectionKeepsItsReason() throws Exception {
        when(importService.addFromJira(any(), eq(ACTOR)))
                .thenThrow(new RoadmapRejectedException(RoadmapRejection.ROADMAP_TOO_MANY_ITEMS));

        send(post(BASE + "/items/jira"), "{\"keys\":[\"CNV-1\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_TOO_MANY_ITEMS"));
    }

    @Test
    void duplicateKeyIsAConflict() throws Exception {
        when(importService.addFromJira(any(), any()))
                .thenThrow(new RoadmapRejectedException(RoadmapRejection.ROADMAP_JIRA_KEY_DUPLICATE));

        send(post(BASE + "/items/jira"), "{\"keys\":[\"CNV-1\"]}").andExpect(status().isConflict());
    }

    @Test
    void unknownItemIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.history(id)).thenThrow(new RoadmapItemNotFoundException());

        mockMvc.perform(get(BASE + "/items/" + id + "/history").with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("ROADMAP_ITEM_NOT_FOUND"));
    }

    @Test
    void historyShowsTheResolvedActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.history(id)).thenReturn(List.of(
                new InputChange(id, RiceField.REACH, null, new BigDecimal("5"), NOW, ACTOR)));
        when(actorResolution.resolve(ACTOR)).thenReturn(new ResolvedActors(Map.of(ACTOR, "amr@example.com")));

        mockMvc.perform(get(BASE + "/items/" + id + "/history").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].field").value("REACH"))
                .andExpect(jsonPath("$.entries[0].changedBy").value("amr@example.com"));
    }

    @Test
    void csvHasHeadersBomAndNeutralisedFormulas() throws Exception {
        when(service.ranked(any())).thenReturn(List.of(
                new RankedItem(item("=HYPERLINK(\"x\")", scored()), new BigDecimal("12.46"), 1)));

        byte[] body = mockMvc.perform(get(BASE + "/export.csv").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=utf-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"roadmap-2026-10-25.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body[0]).isEqualTo((byte) 0xEF);
        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(csv).contains("1,,").contains(",12.5,5,1,80,0.32,Core,Idea,");
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get(BASE + "/items")).andExpect(status().isUnauthorized());
    }
}
