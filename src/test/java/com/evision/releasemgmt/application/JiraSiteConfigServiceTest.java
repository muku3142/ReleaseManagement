package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraSiteConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraSiteConfigServiceTest {

    private final JiraSiteConfigRepository repository = mock(JiraSiteConfigRepository.class);
    private final JiraSiteConfigService service = new JiraSiteConfigService(repository);

    @Test
    void seedsTheConfiguredUrlWhenTheTableIsEmpty() {
        when(repository.find()).thenReturn(Optional.empty());

        service.seedIfAbsent("https://evisionegypt.atlassian.net");

        verify(repository).save(any(JiraSiteConfig.class));
    }

    @Test
    void doesNotOverwriteAnExistingRow() {
        when(repository.find()).thenReturn(
                Optional.of(new JiraSiteConfig("https://already-set.atlassian.net", Instant.now(), "someone")));

        service.seedIfAbsent("https://different.atlassian.net");

        verify(repository, never()).save(any());
    }

    /**
     * RM_JIRA_DEFAULT_BASE_URL bypasses the REST DTO's @Pattern, so without this the seeder
     * would persist a malformed URL and every outbound Jira call would build something like
     * "https://site.atlassian.net//rest/api/3/myself". A trailing slash is the realistic typo.
     */
    @Test
    void refusesToSeedAMalformedSiteUrl() {
        assertThat(new String[]{
                "https://evisionegypt.atlassian.net/",
                "http://evisionegypt.atlassian.net",
                "https://evil.example.com",
                "evisionegypt.atlassian.net",
                "https://.atlassian.net",
                ""
        }).allSatisfy(bad ->
                assertThatThrownBy(() -> service.seedIfAbsent(bad))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("RM_JIRA_DEFAULT_BASE_URL"));

        verify(repository, never()).save(any());
    }
}
