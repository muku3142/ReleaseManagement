package com.evision.releasemgmt.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

@WebMvcTest(SpaForwardingController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class SpaForwardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spaDeepLinksForwardToIndexHtml() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void nestedSpaDeepLinksForwardToIndexHtml() throws Exception {
        mockMvc.perform(get("/releases/some-release"))
                .andExpect(forwardedUrl("/index.html"));
    }

    /**
     * Regression guard. `assets` is dot-free, so before it was excluded the second mapping
     * pattern matched "/assets/<file>" and every Vite bundle came back as index.html with
     * Content-Type text/html — browsers reject the module script under strict MIME checking
     * and the SPA renders blank.
     *
     * <p>The assertion is deliberately "not forwarded" rather than a status code: whether
     * these paths resolve to a real file depends on whether the frontend has been built into
     * target/classes/static, which varies between `mvn test` and a bare IDE run. Forwarding
     * is the defect; resolution is the resource handler's job.
     */
    @Test
    void assetRequestsAreNotForwardedToIndexHtml() throws Exception {
        mockMvc.perform(get("/assets/index-BJiZJ4nx.js")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/assets/index-BiO8dE8o.css")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/assets/nested/deep/chunk-abc123.js")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/favicon.svg")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/favicon.ico")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/apple-touch-icon.png")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/brand/productops-mark.svg")).andExpect(forwardedUrl(null));
    }

    @Test
    void apiAndActuatorPathsAreNotForwarded() throws Exception {
        mockMvc.perform(get("/api/anything")).andExpect(forwardedUrl(null));
        mockMvc.perform(get("/actuator/anything")).andExpect(forwardedUrl(null));
    }
}
