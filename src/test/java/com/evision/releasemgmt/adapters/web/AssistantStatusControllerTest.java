package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.config.AssistantProperties;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.RagServiceException;
import com.evision.releasemgmt.ports.RagStatusPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantStatusController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class, AssistantStatusControllerTest.AssistantOn.class})
@TestPropertySource(properties = "app.assistant.enabled=true")
class AssistantStatusControllerTest {

    @TestConfiguration
    static class AssistantOn {
        @Bean
        AssistantProperties assistantProperties() {
            AssistantProperties.Timeouts timeouts = new AssistantProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(5));
            return new AssistantProperties(true, "http://rag.example:8081", "k", "nomic-embed-text",
                    new AssistantProperties.Sweep(Duration.ofSeconds(10), Duration.ofMinutes(30)), timeouts, timeouts, 4, 6000,
                    new AssistantProperties.Prompts("About \"{release}\". {question}", Map.of(), "I could not: {error}"),
                    new AssistantProperties.Starters(List.of("u1", "u2", "u3"), List.of("r1 {release}", "r2", "r3")),
                    20, Duration.ofMinutes(5),
                    new AssistantProperties.DocumentAssist(4096, 3.0, 152, 2000, new AssistantProperties.Instructions(
                            new AssistantProperties.ByType("a", "a", "a"), new AssistantProperties.ByType("a", "a", "a")), java.time.Duration.ofMinutes(20)));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagStatusPort ragStatus;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("acc-1").claim("token_type", "access"));
    }

    @Test
    void statusWithSwitchOnReturnsStarters() throws Exception {
        mockMvc.perform(get("/api/assistant/status").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.starters.unscoped.length()").value(3))
                .andExpect(jsonPath("$.starters.release.length()").value(3))
                .andExpect(jsonPath("$.starters.release[0]").value("r1 {release}"));
        org.mockito.Mockito.verifyNoInteractions(ragStatus);
    }

    @Test
    void freshnessReturnsTheNewestInstant() throws Exception {
        when(ragStatus.lastIndexedAt(IndexCollection.TICKETS)).thenReturn(Optional.of(Instant.parse("2026-10-15T08:00:00Z")));
        when(ragStatus.lastIndexedAt(IndexCollection.DOCUMENTS)).thenReturn(Optional.of(Instant.parse("2026-10-15T09:30:00Z")));
        when(ragStatus.lastIndexedAt(IndexCollection.MANUAL_ITEMS)).thenReturn(Optional.empty());
        when(ragStatus.lastIndexedAt(IndexCollection.CODE_LINKS)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assistant/freshness").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastIndexedAt").value("2026-10-15T09:30:00Z"));
    }

    @Test
    void freshnessWithNothingIndexedIsNull() throws Exception {
        when(ragStatus.lastIndexedAt(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assistant/freshness").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"lastIndexedAt\":null}"));
    }

    @Test
    void freshnessWhenRagServiceFailsIs503WithoutLeakingDetail() throws Exception {
        when(ragStatus.lastIndexedAt(any()))
                .thenThrow(new RagServiceException("rag status failed collection=rm-tickets id=-", 500));

        mockMvc.perform(get("/api/assistant/freshness").with(authenticated()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("ASSISTANT_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rm-tickets"))));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/assistant/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/assistant/freshness")).andExpect(status().isUnauthorized());
    }

    @Test
    void switchOffReportsDisabledAndHasNoFreshness() {
        AssistantStatusController off = new AssistantStatusController(false,
                new StaticListableBeanFactory().getBeanProvider(AssistantProperties.class),
                new StaticListableBeanFactory().getBeanProvider(RagStatusPort.class));

        assertThat(off.status().enabled()).isFalse();
        assertThat(off.status().starters()).isNull();
        assertThat(off.freshness().getStatusCode().value()).isEqualTo(404);
    }
}
