package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ProductService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductJiraKeyTakenException;
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
import java.util.Map;
import java.util.UUID;

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

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class ProductControllerTest {

    private static final String BASE = "/api/products";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    private final Product zeta = new Product(UUID.randomUUID(), "zeta", null);
    private final Product alpha = new Product(UUID.randomUUID(), "Alpha", null);
    private final Product old = new Product(UUID.randomUUID(), "Aardvark", Instant.parse("2026-01-01T00:00:00Z"));

    @BeforeEach
    void products() {
        when(service.list()).thenReturn(List.of(old, zeta, alpha));
        when(service.clientCounts()).thenReturn(Map.of(alpha.id(), 3L));
        when(service.releaseCounts()).thenReturn(Map.of(zeta.id(), 2L));
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
    void listIsOrderedByNameWithArchivedLastAndCarriesCounts() throws Exception {
        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("Alpha"))
                .andExpect(jsonPath("$.products[0].clientCount").value(3))
                .andExpect(jsonPath("$.products[0].releaseCount").value(0))
                .andExpect(jsonPath("$.products[1].name").value("zeta"))
                .andExpect(jsonPath("$.products[1].releaseCount").value(2))
                .andExpect(jsonPath("$.products[2].name").value("Aardvark"))
                .andExpect(jsonPath("$.products[2].archived").value(true));
    }

    @Test
    void postCreatesAndReturnsTheList() throws Exception {
        send(post(BASE), "{\"name\":\"New\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.products.length()").value(3));
        verify(service).add("New");
    }

    @Test
    void blankAndOverlongNamesAreRejected() throws Exception {
        send(post(BASE), "{\"name\":\" \"}").andExpect(status().isBadRequest());
        send(put(BASE + "/{id}", alpha.id()), "{\"name\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateNameIsAConflict() throws Exception {
        doThrow(new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_NAME_DUPLICATE))
                .when(service).add("Alpha");

        send(post(BASE), "{\"name\":\"Alpha\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PRODUCT_NAME_DUPLICATE"));
    }

    @Test
    void renameArchiveAndUnarchiveDelegate() throws Exception {
        send(put(BASE + "/{id}", alpha.id()), "{\"name\":\"Beta\"}").andExpect(status().isOk());
        send(post(BASE + "/{id}/archive", alpha.id())).andExpect(status().isOk());
        send(post(BASE + "/{id}/unarchive", old.id())).andExpect(status().isOk());

        verify(service).rename(alpha.id(), "Beta");
        verify(service).archive(alpha.id());
        verify(service).unarchive(old.id());
    }

    @Test
    void putJiraProjectsSetsKeysAndListCarriesThem() throws Exception {
        when(service.jiraProjectKeys()).thenReturn(Map.of(alpha.id(), List.of("CNV")));

        send(put(BASE + "/{id}/jira-projects", alpha.id()), "{\"keys\":[\"CNV\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].jiraProjectKeys[0]").value("CNV"))
                .andExpect(jsonPath("$.products[1].jiraProjectKeys.length()").value(0));
        verify(service).setJiraProjectKeys(alpha.id(), List.of("CNV"));
    }

    @Test
    void listShowsAnEmptyKeyListForAKeylessProduct() throws Exception {
        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(jsonPath("$.products[0].jiraProjectKeys.length()").value(0));
    }

    @Test
    void invalidKeyIsABadRequest() throws Exception {
        doThrow(new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_JIRA_KEY_INVALID))
                .when(service).setJiraProjectKeys(alpha.id(), List.of("1AB"));

        send(put(BASE + "/{id}/jira-projects", alpha.id()), "{\"keys\":[\"1AB\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("PRODUCT_JIRA_KEY_INVALID"));
    }

    @Test
    void takenKeyIsAConflictNamingTheOwner() throws Exception {
        doThrow(new ProductJiraKeyTakenException("CNV", "Beta"))
                .when(service).setJiraProjectKeys(alpha.id(), List.of("CNV"));

        send(put(BASE + "/{id}/jira-projects", alpha.id()), "{\"keys\":[\"CNV\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PRODUCT_JIRA_KEY_TAKEN"))
                .andExpect(jsonPath("$.key").value("CNV"))
                .andExpect(jsonPath("$.productName").value("Beta"));
    }

    @Test
    void nullOrTooManyKeysFailValidation() throws Exception {
        send(put(BASE + "/{id}/jira-projects", alpha.id()), "{\"keys\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        String eleven = String.join(",", java.util.Collections.nCopies(11, "\"CNV\""));
        send(put(BASE + "/{id}/jira-projects", alpha.id()), "{\"keys\":[" + eleven + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
    }

    @Test
    void jiraProjectsRequireAuthentication() throws Exception {
        mockMvc.perform(put(BASE + "/{id}/jira-projects", alpha.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thereIsNoDeleteRoute() throws Exception {
        send(delete(BASE + "/{id}", alpha.id())).andExpect(status().isMethodNotAllowed());
    }
}
