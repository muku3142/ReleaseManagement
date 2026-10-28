package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.security.CookieBearerTokenResolver;
import com.evision.releasemgmt.adapters.security.ProblemDetailAuthenticationEntryPoint;
import com.evision.releasemgmt.adapters.web.dto.BriefingSettingsRequest;
import com.evision.releasemgmt.application.briefing.BriefingRunNowService;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.ClaudeFeed;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.SchedulerUser;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.TaskView;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.Update;
import com.evision.releasemgmt.application.briefing.BriefingSettingsService.View;
import com.evision.releasemgmt.config.JwtConfig;
import com.evision.releasemgmt.config.SecurityConfig;
import com.evision.releasemgmt.domain.briefing.BriefingRejectedException;
import com.evision.releasemgmt.domain.briefing.BriefingRejection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BriefingSettingsController.class)
@Import({SecurityConfig.class, JwtConfig.class, CookieBearerTokenResolver.class, ProblemDetailAuthenticationEntryPoint.class})
class BriefingSettingsControllerTest {

    private static final String BASE = "/api/settings/briefings";
    private static final String TASK = """
            {"id":"%s","enabled":true,"days":["SUN"],"time":"%s","staleAfterWorkingDays":3,"dueSoonDays":7,"backlogItems":20}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BriefingSettingsService settings;

    @MockitoBean
    private BriefingRunNowService runNow;

    @Test
    void getReturnsTheSettingsView() throws Exception {
        when(settings.view()).thenReturn(view());

        mockMvc.perform(get(BASE).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claudeFeed.dataDir").value("/data/briefings"))
                .andExpect(jsonPath("$.schedulerUser.isYou").value(true))
                .andExpect(jsonPath("$.credentialProblem").value(false))
                .andExpect(jsonPath("$.fallbackEnabled").value(false))
                .andExpect(jsonPath("$.tasks[0].time").value("08:44"))
                .andExpect(jsonPath("$.tasks[0].days[0]").value("SUN"));
    }

    @Test
    void putSavesTheMappedUpdate() throws Exception {
        when(settings.save(any())).thenReturn(view());

        mockMvc.perform(write(put(BASE)).content(body(30, "08:44")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graceMinutes").value(30));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(settings).save(update.capture());
        assertThat(update.getValue().graceMinutes()).isEqualTo(30);
        assertThat(update.getValue().tasks()).hasSize(3);
        assertThat(update.getValue().tasks().get(0).time()).isEqualTo("08:44");
    }

    @Test
    void putRejectsAGraceOfZero() throws Exception {
        mockMvc.perform(write(put(BASE)).content(body(0, "08:44")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.graceMinutes").exists());
        verify(settings, never()).save(any());
    }

    @Test
    void putRejectsAMalformedTime() throws Exception {
        mockMvc.perform(write(put(BASE)).content(body(30, "8:4")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors['tasks[0].time']").exists());
        verify(settings, never()).save(any());
    }

    @Test
    void putMapsServiceRejectionToSettingsInvalid() throws Exception {
        when(settings.save(any())).thenThrow(new BriefingRejectedException(BriefingRejection.BRIEFING_SETTINGS_INVALID));

        mockMvc.perform(write(put(BASE)).content(body(30, "08:44")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("BRIEFING_SETTINGS_INVALID"));
    }

    @Test
    void designateMeReturnsTheView() throws Exception {
        when(settings.designateMe()).thenReturn(view());

        mockMvc.perform(write(put(BASE + "/scheduler-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulerUser.email").value("rm@acme.com"));
    }

    @Test
    void designateMeWithoutACredentialIs422() throws Exception {
        when(settings.designateMe())
                .thenThrow(new BriefingRejectedException(BriefingRejection.BRIEFING_NO_JIRA_CREDENTIAL));

        mockMvc.perform(write(put(BASE + "/scheduler-user")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.reason").value("BRIEFING_NO_JIRA_CREDENTIAL"));
    }

    @Test
    void runNowReportsCredentialProblemsAsACountNotA401() throws Exception {
        when(runNow.runNow("jira_digest"))
                .thenReturn(new BriefingRunNowService.Result("jira_digest", "Daily Jira digest", 1, 0, 0, 1, 0));

        mockMvc.perform(write(post(BASE + "/tasks/jira_digest/run")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsCredential").value(1))
                .andExpect(jsonPath("$.name").value("Daily Jira digest"));
    }

    @Test
    void runNowMapsNotRunnableAndDisabled() throws Exception {
        when(runNow.runNow("reg_watch"))
                .thenThrow(new BriefingRejectedException(BriefingRejection.BRIEFING_TASK_NOT_RUNNABLE));
        when(runNow.runNow("sprint_check"))
                .thenThrow(new BriefingRejectedException(BriefingRejection.BRIEFING_TASK_DISABLED));

        mockMvc.perform(write(post(BASE + "/tasks/reg_watch/run")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("BRIEFING_TASK_NOT_RUNNABLE"));
        mockMvc.perform(write(post(BASE + "/tasks/sprint_check/run")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("BRIEFING_TASK_DISABLED"));
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(30, "08:44")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/scheduler-user").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/tasks/jira_digest/run").with(csrf())).andExpect(status().isUnauthorized());
        verify(settings, never()).designateMe();
        verify(runNow, never()).runNow(any());
    }

    @Test
    void fallbackSwitchSavesForTheSignedInUser() throws Exception {
        when(settings.view()).thenReturn(view());

        mockMvc.perform(write(put(BASE + "/fallback")).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claudeFeed.dataDir").value("/data/briefings"));

        verify(settings).setFallbackEnabled(true, "557058:abc-123");
    }

    @Test
    void fallbackSwitchNeedsEnabled() throws Exception {
        mockMvc.perform(write(put(BASE + "/fallback")).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.enabled").exists());

        verify(settings, never()).setFallbackEnabled(anyBoolean(), any());
    }

    @Test
    void fallbackSwitchRequiresAuthentication() throws Exception {
        mockMvc.perform(put(BASE + "/fallback").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());

        verify(settings, never()).setFallbackEnabled(anyBoolean(), any());
    }

    @Test
    void theRequestCannotNameAnAccount() {
        assertThat(Arrays.stream(BriefingSettingsRequest.class.getRecordComponents()).map(c -> c.getName()))
                .noneMatch(name -> name.toLowerCase().contains("account"));
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(builder -> builder.subject("557058:abc-123").claim("token_type", "access"));
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request) {
        return request.with(csrf()).with(user()).contentType(MediaType.APPLICATION_JSON);
    }

    private static String body(int grace, String firstTime) {
        return """
                {"graceMinutes":%d,"workDays":["SUN","MON"],"keepRuns":60,"tasks":[%s,%s,%s]}""".formatted(grace,
                TASK.formatted("jira_digest", firstTime), TASK.formatted("sprint_check", "09:41"),
                TASK.formatted("weekly_status", "13:41"));
    }

    private static View view() {
        return new View(new ClaudeFeed("/data/briefings", "/data/briefings/config.json", true),
                new SchedulerUser("rm@acme.com", true), false, false, 30, List.of("SUN", "MON"), 60,
                List.of(new TaskView("jira_digest", "Daily Jira digest", true, List.of("SUN", "MON"), "08:44", 3, 7, 20)));
    }
}
