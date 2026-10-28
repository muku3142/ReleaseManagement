package com.evision.releasemgmt.application.briefing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evision.releasemgmt.application.JiraSiteConfigService;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraCredential;
import com.evision.releasemgmt.domain.briefing.BriefingSettings;
import com.evision.releasemgmt.domain.briefing.WorkCalendar;
import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.BriefingSettingsRepository;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BriefingJiraAccessTest {

    private static final String SITE_URL = "https://acme.atlassian.net";
    private static final String ACCOUNT = "557058:sched";

    private final BriefingSettingsRepository settings = mock(BriefingSettingsRepository.class);
    private final JiraCredentialRepository credentials = mock(JiraCredentialRepository.class);
    private final JiraSiteConfigService site = mock(JiraSiteConfigService.class);
    private final BriefingJiraAccess access = new BriefingJiraAccess(settings, credentials, site);

    private final Logger logger = (Logger) LoggerFactory.getLogger(BriefingJiraAccess.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    private void scheduler(String accountId) {
        when(settings.get()).thenReturn(new BriefingSettings(accountId, 30, WorkCalendar.SUN_TO_THU, null, false, null, null));
    }

    private void credential() {
        when(credentials.findByAccountId(ACCOUNT))
                .thenReturn(Optional.of(new JiraCredential(ACCOUNT, "s@acme.com", "tok-secret", 1, Instant.now())));
    }

    private void siteConfigured() {
        when(site.get()).thenReturn(new JiraSiteConfig(SITE_URL, Instant.now(), "actor"));
    }

    @Test
    void buildsTheContextOfTheDesignatedScheduler() {
        scheduler(ACCOUNT);
        credential();
        siteConfigured();

        assertThat(access.designatedContext())
                .contains(new JiraAccessContext(SITE_URL, ACCOUNT, "s@acme.com", "tok-secret"));
    }

    @Test
    void noSchedulerMeansNoContextAndNoCredentialRead() {
        scheduler(null);

        assertThat(access.designatedContext()).isEmpty();
        verify(credentials, never()).findByAccountId(anyString());
    }

    @Test
    void undecryptableCredentialGivesEmptyAndLogsOnlyTheExceptionType() {
        scheduler(ACCOUNT);
        when(credentials.findByAccountId(ACCOUNT)).thenThrow(new IllegalStateException("tok-secret leaked?"));

        assertThat(access.designatedContext()).isEmpty();
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("IllegalStateException").doesNotContain("tok-secret");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void missingCredentialGivesEmpty() {
        scheduler(ACCOUNT);
        when(credentials.findByAccountId(ACCOUNT)).thenReturn(Optional.empty());

        assertThat(access.designatedContext()).isEmpty();
    }

    @Test
    void siteNotConfiguredGivesEmpty() {
        scheduler(ACCOUNT);
        credential();
        when(site.get()).thenThrow(new JiraSiteConfigService.JiraSiteNotConfiguredException());

        assertThat(access.designatedContext()).isEmpty();
    }

    @Test
    void readsTheCredentialFreshOnEveryCall() {
        scheduler(ACCOUNT);
        credential();
        siteConfigured();

        access.designatedContext();
        access.designatedContext();

        verify(credentials, times(2)).findByAccountId(ACCOUNT);
    }
}
