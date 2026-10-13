package com.evision.ragservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "rag-service", version = "v1",
                description = "Domain-free retrieval service: collections, documents, semantic search. "
                        + "Client routes take a client API key; /v1/admin routes take the admin key. "
                        + "Both as 'Authorization: Bearer <key>'."),
        security = @SecurityRequirement(name = "bearerKey"))
@SecurityScheme(name = "bearerKey", type = SecuritySchemeType.HTTP, scheme = "bearer")
public class OpenApiConfig {
}
