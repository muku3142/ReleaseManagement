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
import com.evision.releasemgmt.domain.roadmap.InputChangeKind;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RiceOverrides;
import com.evision.releasemgmt.domain.roadmap.RoadmapRanking;
import com.evision.releasemgmt.domain.roadmap.RoadmapFilter;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        when(service.unranked(any())).thenAnswer(call -> RoadmapRanking.unranked(call.getArgument(0), 0));
    }

    private static RankedItem ranked(RoadmapItem item, BigDecimal score, Integer rank) {
        return new RankedItem(item, RiceInputs.NONE, item.inputs(), score, rank);
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
                manual.productId(), manual.status(), null, null, null, inputs, RiceOverrides.NONE, null, null, NOW,
                ACTOR, null);
    }

    private static RiceInputs scored() {
        return new RiceInputs(5, new BigDecimal("1"), 80, new BigDecimal("0.32"));
    }

    @Test
    void listCarriesRoundedScoreRankAndProduct() throws Exception {
        when(service.ranked(any())).thenReturn(List.of(
                ranked(item("A", scored()), new BigDecimal("12.46"), 1),
                ranked(item("B", RiceInputs.NONE), null, null)));

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
    void invalidEffortIsRejectedWithItsReason() throws Exception {
        send(put(BASE + "/items/" + UUID.randomUUID() + "/inputs"), "{\"effort\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_EFFORT_INVALID"));
    }

    @Test
    void enteringACalculatedValueIsRejectedAndEffortAlone() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setInputs(eq(id), eq(new RiceInputs(null, new BigDecimal("2"), null, null)), eq(ACTOR)))
                .thenThrow(new RoadmapRejectedException(RoadmapRejection.ROADMAP_FIELD_CALCULATED));
        when(service.setInputs(eq(id), eq(new RiceInputs(null, null, null, new BigDecimal("1.5"))), eq(ACTOR)))
                .thenReturn(item("A", RiceInputs.NONE));

        send(put(BASE + "/items/" + id + "/inputs"), "{\"impact\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_FIELD_CALCULATED"));
        send(put(BASE + "/items/" + id + "/inputs"), "{\"effort\":1.5}")
                .andExpect(status().isOk());
    }

    @Test
    void listCarriesCalculatedValuesOverridesAndJiraFacts() throws Exception {
        RoadmapItem base = item("A", new RiceInputs(20, null, null, BigDecimal.ONE));
        RoadmapItem overridden = new RoadmapItem(base.id(), RoadmapItemSource.JIRA, "CNV-1", "101", "A", "Open",
                NOW, NOW, null, base.productId(), base.status(), null, null, null, base.inputs(),
                new RiceOverrides("Big client onboarding", null, null), "High", 2, NOW, ACTOR, null);
        when(service.ranked(any())).thenReturn(List.of(RoadmapRanking.unranked(overridden, 7)));

        mockMvc.perform(get(BASE + "/items").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reach").value(20))
                .andExpect(jsonPath("$.items[0].impact").value(2))
                .andExpect(jsonPath("$.items[0].confidence").value(80))
                .andExpect(jsonPath("$.items[0].calculated.reach").value(7))
                .andExpect(jsonPath("$.items[0].calculated.impact").value(2))
                .andExpect(jsonPath("$.items[0].overrides.reach").value("Big client onboarding"))
                .andExpect(jsonPath("$.items[0].overrides.impact").doesNotExist())
                .andExpect(jsonPath("$.items[0].calculatedFields[0]").value("REACH"))
                .andExpect(jsonPath("$.items[0].calculatedFields.length()").value(3))
                .andExpect(jsonPath("$.items[0].jiraPriority").value("High"))
                .andExpect(jsonPath("$.items[0].readinessSignals").value(2));
    }

    @Test
    void overrideSendsValueAndReasonForTheActor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.override(id, RiceField.REACH, new BigDecimal("20"), "Big client onboarding", ACTOR))
                .thenReturn(item("A", RiceInputs.NONE));

        send(put(BASE + "/items/" + id + "/overrides/REACH"), "{\"value\":20,\"reason\":\"Big client onboarding\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A"));

        verify(service).override(id, RiceField.REACH, new BigDecimal("20"), "Big client onboarding", ACTOR);
    }

    @Test
    void overrideNeedsAReasonOfAtMost200Characters() throws Exception {
        String url = BASE + "/items/" + UUID.randomUUID() + "/overrides/REACH";
        send(put(url), "{\"value\":20}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        send(put(url), "{\"value\":20,\"reason\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        send(put(url), "{\"value\":20,\"reason\":\"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        send(put(url), "{\"reason\":\"why\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void overrideOfANonCalculatedOrUnknownFieldIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.override(eq(id), eq(RiceField.EFFORT), any(), any(), eq(ACTOR)))
                .thenThrow(new RoadmapRejectedException(RoadmapRejection.ROADMAP_FIELD_NOT_CALCULATED));

        send(put(BASE + "/items/" + id + "/overrides/EFFORT"), "{\"value\":1,\"reason\":\"why\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ROADMAP_FIELD_NOT_CALCULATED"));
        send(put(BASE + "/items/" + id + "/overrides/FOO"), "{\"value\":1,\"reason\":\"why\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetReturnsTheItemBackOnItsCalculatedValue() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.resetOverride(id, RiceField.REACH, ACTOR)).thenReturn(item("A", RiceInputs.NONE));

        mockMvc.perform(delete(BASE + "/items/" + id + "/overrides/REACH").with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reach").value(0))
                .andExpect(jsonPath("$.overrides.reach").doesNotExist());

        verify(service).resetOverride(id, RiceField.REACH, ACTOR);
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
                "{\"impact\":1,\"confidence\":80,\"effort\":0.32}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.scoreDisplay").value("12.5"));

        verify(service).setInputs(eq(id),
                eq(new RiceInputs(null, new BigDecimal("1"), 80, new BigDecimal("0.32"))), eq(ACTOR));
    }

    @Test
    void manualAddWithoutAProductFailsValidation() throws Exception {
        send(post(BASE + "/items"), "{\"title\":\"Idea\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void detailsWithANullProductFailsValidation() throws Exception {
        send(put(BASE + "/items/" + UUID.randomUUID() + "/details"), "{\"status\":\"IDEA\",\"productId\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
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
                new InputChange(id, RiceField.REACH, null, new BigDecimal("5"), NOW, ACTOR,
                        InputChangeKind.OVERRIDDEN, "Big client")));
        when(actorResolution.resolve(ACTOR)).thenReturn(new ResolvedActors(Map.of(ACTOR, "amr@example.com")));

        mockMvc.perform(get(BASE + "/items/" + id + "/history").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].field").value("REACH"))
                .andExpect(jsonPath("$.entries[0].changedBy").value("amr@example.com"))
                .andExpect(jsonPath("$.entries[0].kind").value("OVERRIDDEN"))
                .andExpect(jsonPath("$.entries[0].reason").value("Big client"));
    }

    @Test
    void csvListsOverriddenFields() throws Exception {
        RoadmapItem base = item("B", new RiceInputs(20, null, null, BigDecimal.ONE));
        RoadmapItem overridden = new RoadmapItem(base.id(), RoadmapItemSource.JIRA, "CNV-1", "101", "B", "Open",
                NOW, NOW, null, base.productId(), base.status(), null, null, null,
                new RiceInputs(20, BigDecimal.ONE, null, BigDecimal.ONE),
                new RiceOverrides("why", "because", null), "High", 2, NOW, ACTOR, null);
        when(service.ranked(any())).thenReturn(List.of(RoadmapRanking.unranked(overridden, 7)));

        String csv = mockMvc.perform(get(BASE + "/export.csv").with(authenticated()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains(",Archived,Overridden");
        assertThat(csv).contains(",20,1,80,1,Core,").contains(",no,no,Reach; Impact");
    }

    @Test
    void csvHasHeadersBomAndNeutralisedFormulas() throws Exception {
        when(service.ranked(any())).thenReturn(List.of(
                ranked(item("=HYPERLINK(\"x\")", scored()), new BigDecimal("12.46"), 1)));

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
