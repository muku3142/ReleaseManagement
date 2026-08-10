package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.application.ReleaseViewAssembler;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.LogoRejectedException;
import com.evision.releasemgmt.domain.template.LogoRejectionReason;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TMPL-09: upload/replace/remove/download plus the three distinct rejection reasons the SPA maps
// to its own sentence (UI-SPEC planning input #1).
@WebMvcTest(TemplateController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class,
         ProblemDetailAuthenticationEntryPoint.class})
class TemplateLogoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @MockitoBean
    private ReleaseService releaseService;

    @MockitoBean
    private ReleaseViewAssembler assembler;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(b -> b.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static Template template(UUID id, LogoMetadata logo) {
        Instant now = Instant.parse("2026-08-09T09:00:00Z");
        return new Template(id, "Standard notes", TemplateDocumentType.RELEASE_NOTES, "Body", true,
                ChromeFields.empty(), GroupingDimension.ISSUE_TYPE, logo, List.of(), now, "557058:abc-123",
                now, "557058:abc-123");
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void uploadingAValidPngReturns200WithLogoMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-09T09:00:00Z");
        LogoMetadata metadata = new LogoMetadata("image/png", "logo.png", 123, 10, 10, now);
        when(templateService.uploadLogo(eq(id), any(), any())).thenReturn(template(id, metadata));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/api/templates/{id}/logo", id).file(file)
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo.contentType").value("image/png"));
    }

    @Test
    void uploadRejectedAsInvalidTypeReturns415() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.uploadLogo(eq(id), any(), any()))
                .thenThrow(new LogoRejectedException(LogoRejectionReason.INVALID_TYPE));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png",
                "<?xml version=\"1.0\"?><svg/>".getBytes());

        mockMvc.perform(multipart("/api/templates/{id}/logo", id).file(file)
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.reason").value("LOGO_INVALID_TYPE"));
    }

    @Test
    void uploadRejectedAsTooLargeReturns413() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.uploadLogo(eq(id), any(), any()))
                .thenThrow(new LogoRejectedException(LogoRejectionReason.TOO_LARGE));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/api/templates/{id}/logo", id).file(file)
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("LOGO_TOO_LARGE"));
    }

    @Test
    void uploadRejectedAsDimensionsExceededReturns422() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.uploadLogo(eq(id), any(), any()))
                .thenThrow(new LogoRejectedException(LogoRejectionReason.DIMENSIONS_EXCEEDED));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngBytes());

        mockMvc.perform(multipart("/api/templates/{id}/logo", id).file(file)
                        .with(csrf()).with(authenticated()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("LOGO_DIMENSIONS_EXCEEDED"));
    }

    @Test
    void removingALogoReturns200WithNoLogo() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.removeLogo(id)).thenReturn(template(id, null));

        mockMvc.perform(delete("/api/templates/{id}/logo", id).with(csrf()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo").doesNotExist());

        verify(templateService).removeLogo(id);
    }

    @Test
    void downloadingALogoReturnsTheStoredBytesWithNosniff() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = pngBytes();
        Instant now = Instant.parse("2026-08-09T09:00:00Z");
        LogoImage image = new LogoImage(bytes, new LogoMetadata("image/png", "logo.png", bytes.length,
                10, 10, now));
        when(templateService.getLogo(id)).thenReturn(java.util.Optional.of(image));

        mockMvc.perform(get("/api/templates/{id}/logo", id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    org.assertj.core.api.Assertions.assertThat(body).isEqualTo(bytes);
                });
    }

    @Test
    void downloadingALogoForATemplateThatDoesNotExistReturns404WithTemplateNotFoundReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.getLogo(id)).thenThrow(new TemplateNotFoundException());

        mockMvc.perform(get("/api/templates/{id}/logo", id).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("TEMPLATE_NOT_FOUND"));
    }

    // WR-04: a template that plainly exists but simply has no logo must not be reported as "this
    // template doesn't exist" - it gets a plain 404 with no misleading reason.
    @Test
    void downloadingALogoForARealTemplateWithNoLogoReturnsPlain404WithoutTemplateNotFoundReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.getLogo(id)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/templates/{id}/logo", id).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }
}
