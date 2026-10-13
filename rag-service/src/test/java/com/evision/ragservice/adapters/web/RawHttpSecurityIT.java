package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.evision.ragservice.PgVectorImage;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    @Test
    void unknownPathsFailClosed() throws Exception {
        assertThat(send(request("/").GET().build()).statusCode()).isEqualTo(401);
        assertThat(send(request("/v2/anything").GET().build()).statusCode()).isEqualTo(401);
        assertThat(send(request("/actuator/health/readiness").GET().build()).statusCode()).isEqualTo(200);
    }
}
