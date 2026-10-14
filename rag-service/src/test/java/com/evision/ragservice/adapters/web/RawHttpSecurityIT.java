package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.PgVectorImage;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Real Tomcat, not MockMvc: these cases depend on the raw request line the container receives
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RawHttpSecurityIT {

    private static final String[] ENCODED_ADMIN_PATHS = {
            "/%761/admin/clients", "/v1/%61dmin/clients", "/v1//admin/clients", "/v1;x=1/admin/clients"};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Value("${local.server.port}")
    private int port;

    @Value("${rag.admin-key}")
    private String adminKey;

    @Value("${rag.limits.max-request-bytes}")
    private long maxRequestBytes;

    @Autowired
    private JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM chunk").update();
        jdbc.sql("DELETE FROM document").update();
        jdbc.sql("DELETE FROM collection").update();
        jdbc.sql("DELETE FROM client_app").update();
    }

    private HttpRequest.Builder request(String rawPath) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> createClient(String path, String key, String name) throws Exception {
        HttpRequest.Builder builder = request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"));
        if (key != null) {
            builder.header("Authorization", "Bearer " + key);
        }
        return send(builder.build());
    }

    @Test
    void encodedAdminPathsNeedTheAdminKey() throws Exception {
        for (String path : ENCODED_ADMIN_PATHS) {
            assertThat(send(request(path).GET().build()).statusCode()).as("GET " + path).isEqualTo(401);
            assertThat(createClient(path, null, "intruder").statusCode()).as("POST " + path).isEqualTo(401);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM client_app").query(Long.class).single()).isZero();
    }

    @Test
    void clientKeyCannotReachAdminThroughAnEncodedPath() throws Exception {
        String clientKey = JsonPath.read(createClient("/v1/admin/clients", adminKey, "alpha").body(), "$.apiKey");

        for (String path : ENCODED_ADMIN_PATHS) {
            HttpRequest list = request(path).header("Authorization", "Bearer " + clientKey).GET().build();
            assertThat(send(list).statusCode()).as("GET " + path).isNotEqualTo(200);
            assertThat(createClient(path, clientKey, "beta").statusCode()).as("POST " + path).isNotEqualTo(201);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM client_app").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void adminKeyStillWorksThroughAnEncodedPath() throws Exception {
        HttpRequest list = request("/v1/%61dmin/clients").header("Authorization", "Bearer " + adminKey).GET().build();

        assertThat(send(list).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> createClientChunked(String name) throws Exception {
        byte[] body = ("{\"name\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
        // ofInputStream has no known length, so the JDK client sends Transfer-Encoding: chunked
        HttpRequest create = request("/v1/admin/clients").header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminKey)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))).build();
        return send(create);
    }

    @Test
    void chunkedBodyOverTheLimitIsRejected() throws Exception {
        HttpResponse<String> response = createClientChunked("a".repeat((int) maxRequestBytes + 1024));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(JsonPath.<String>read(response.body(), "$.reason")).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(jdbc.sql("SELECT count(*) FROM client_app").query(Long.class).single()).isZero();
    }

    @Test
    void chunkedBodyUnderTheLimitIsAccepted() throws Exception {
        assertThat(createClientChunked("alpha").statusCode()).isEqualTo(201);
    }

    @Test
    void chatWithoutAValidKeyIsProblemJsonNeverAnEventStream() throws Exception {
        for (String key : new String[] {null, "wrong-key"}) {
            HttpRequest.Builder builder = request("/v1/chat").header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"collections\":[\"docs\"],\"question\":\"q\"}"));
            if (key != null) {
                builder.header("Authorization", "Bearer " + key);
            }
            HttpResponse<String> response = send(builder.build());

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).doesNotContain("text/event-stream");
            assertThat(response.body()).contains("\"reason\"");
        }
    }

    @Test
    void unknownPathsFailClosed()throws Exception {
        assertThat(send(request("/").GET().build()).statusCode()).isEqualTo(401);
        assertThat(send(request("/v2/anything").GET().build()).statusCode()).isEqualTo(401);
        assertThat(send(request("/actuator/health/readiness").GET().build()).statusCode()).isEqualTo(200);
    }
}
