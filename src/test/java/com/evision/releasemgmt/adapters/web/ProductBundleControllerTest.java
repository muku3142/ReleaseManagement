package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ProductBundleService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.portfolio.ProductBundle;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductBundleController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class ProductBundleControllerTest {

    private static final String BASE = "/api/products/{id}/bundles";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductBundleService service;

    private final UUID productId = UUID.randomUUID();
    private final ProductBundle payments = new ProductBundle(UUID.randomUUID(), "Payments", List.of("A-1", "A-2"));

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(authenticated())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void getListsTheBundles() throws Exception {
        when(service.list(productId)).thenReturn(List.of(payments));

        mockMvc.perform(get(BASE, productId).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundles[0].id").value(payments.id().toString()))
                .andExpect(jsonPath("$.bundles[0].name").value("Payments"))
                .andExpect(jsonPath("$.bundles[0].ticketKeys[1]").value("A-2"));
    }

    @Test
    void postSavesAndReturnsTheList() throws Exception {
        when(service.list(productId)).thenReturn(List.of(payments));

        send(post(BASE, productId), "{\"name\":\"Payments\",\"ticketKeys\":[\"A-1\",\"A-2\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundles.length()").value(1));
        verify(service).save(productId, "Payments", List.of("A-1", "A-2"));
    }

    @Test
    void blankNameAndMalformedKeysFailValidation() throws Exception {
        send(post(BASE, productId), "{\"name\":\" \",\"ticketKeys\":[\"A-1\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        send(post(BASE, productId), "{\"name\":\"x\",\"ticketKeys\":[\"a-1\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
        send(post(BASE, productId), "{\"name\":\"x\",\"ticketKeys\":[]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturnsTheList() throws Exception {
        when(service.list(productId)).thenReturn(List.of());

        mockMvc.perform(delete(BASE + "/{bundleId}", productId, payments.id()).with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundles.length()").value(0));
        verify(service).delete(productId, payments.id());
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        when(service.list(productId)).thenThrow(new ProductNotFoundException());

        mockMvc.perform(get(BASE, productId).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("PRODUCT_NOT_FOUND"));
    }
}
