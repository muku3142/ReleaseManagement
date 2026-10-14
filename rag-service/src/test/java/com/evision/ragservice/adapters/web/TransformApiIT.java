package com.evision.ragservice.adapters.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.FakeOllamaServer;
import com.evision.ragservice.PgVectorImage;
import com.evision.ragservice.SseEvents;
import com.evision.ragservice.application.ClientService;
import com.evision.ragservice.application.GenerationGate;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransformApiIT {

    @RegisterExtension
    static WireMockExtension ollama = WireMockExtension.newInstance().options(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @DynamicPropertySource
    static void ollamaUrl(DynamicPropertyRegistry registry) {
        registry.add("rag.ollama.base-url", ollama::baseUrl);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ClientService clients;
    @Autowired
    private GenerationGate gate;

    private UUID clientA;
    private String keyA;

    @BeforeEach
    void setUp() {
        ollama.resetAll();
        jdbc.sql("DELETE FROM client_app").update();
        ClientService.IssuedKey issued = clients.register("client-a");
        clientA = issued.client().id();
        keyA = issued.apiKey();
        ollama.stubFor(WireMock.post(urlEqualTo("/api/show")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"capabilities\":[\"completion\"]}")));
    }

    private ResultActions transform(String text, String instruction) throws Exception {
        return mvc.perform(post("/v1/transform").header(HttpHeaders.AUTHORIZATION, "Bearer " + keyA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\",\"instruction\":\"" + instruction + "\"}"));
    }

    @Test
    void streamsStatusTokensAndDoneWithoutCitationsOrEmbedding() throws Exception {
        ollama.stubFor(WireMock.post(urlEqualTo("/api/chat")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/x-ndjson")
                .withBody(String.join("\n", FakeOllamaServer.tokenLine("Short"), FakeOllamaServer.tokenLine("er."),
                        FakeOllamaServer.doneLine("stop", 50, 3)) + "\n")));

        MvcResult result = transform("A long paragraph.", "Shorten it")
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10_000);

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        List<String> names = SseEvents.parse(result.getResponse().getContentAsString()).stream()
                .map(SseEvents.Event::name).toList();
        assertThat(names).startsWith("status", "token").endsWith("token", "done").doesNotContain("citations");
        ollama.verify(0, postRequestedFor(urlEqualTo("/api/embed")));
        assertThat(gate.anyInFlight()).isFalse();
    }

    @Test
    void oversizedTextIsPayloadTooLargeBeforeAnyEvent() throws Exception {
        transform("x".repeat(30_000), "Shorten it")
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.reason").value("TEXT_TOO_LARGE"));
    }

    @Test
    void oversizedInstructionAndBlankTextAreBadRequests() throws Exception {
        transform("text", "i".repeat(2001))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("INVALID_CONTENT"));
        transform(" ", "Shorten it")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void sharesTheGenerationGateWithChat() throws Exception {
        assertThat(gate.tryAcquire(clientA)).isTrue();
        try {
            transform("text", "Shorten it")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(jsonPath("$.reason").value("TOO_MANY_REQUESTS"));
        } finally {
            gate.release(clientA);
        }
    }
}
