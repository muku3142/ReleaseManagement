package com.evision.ragservice.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evision.ragservice.PgVectorImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiContractIT {

    private static final Path COMMITTED_CONTRACT = Path.of("openapi.json");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private MockMvc mvc;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode generated() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void everyHandlerIsDocumented() throws Exception {
        JsonNode paths = generated().path("paths");
        List<String> missing = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (var pattern : info.getPathPatternsCondition().getPatterns()) {
                if (!pattern.getPatternString().startsWith("/v1/")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    if (paths.path(pattern.getPatternString()).path(method.name().toLowerCase()).isMissingNode()) {
                        missing.add(method + " " + pattern.getPatternString());
                    }
                }
            }
        }
        assertThat(missing).as("handlers missing from /v3/api-docs").isEmpty();
    }

    @Test
    void securitySchemeDeclared() throws Exception {
        assertThat(generated().at("/components/securitySchemes/bearerKey/scheme").asString()).isEqualTo("bearer");
    }

    @Test
    void committedContractMatchesGenerated() throws Exception {
        JsonNode generated = generated();
        if (Boolean.getBoolean("rag.contract.update")) {
            String pretty = json.writerWithDefaultPrettyPrinter().writeValueAsString(generated);
            Files.writeString(COMMITTED_CONTRACT, pretty.replace("\r\n", "\n") + "\n");
        }
        JsonNode committed = json.readTree(Files.readString(COMMITTED_CONTRACT));
        assertThat(generated).as("openapi.json is stale; regenerate with "
                + "mvn -f rag-service/pom.xml test -Dtest=OpenApiContractIT -Drag.contract.update=true")
                .isEqualTo(committed);
    }
}
