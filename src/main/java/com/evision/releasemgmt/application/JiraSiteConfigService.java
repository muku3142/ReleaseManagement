package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.JiraSiteConfig;
import com.evision.releasemgmt.ports.JiraSiteConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class JiraSiteConfigService {

    private static final String SEED_ACTOR = "application-config-seed";

    /** Same shape UpdateJiraSiteConfigRequest enforces at the REST boundary. */
    static final Pattern JIRA_CLOUD_URL =
            Pattern.compile("^https://[A-Za-z0-9][A-Za-z0-9-]*\\.atlassian\\.net$");

    private final JiraSiteConfigRepository repository;

    public JiraSiteConfigService(JiraSiteConfigRepository repository) {
        this.repository = repository;
    }

    public JiraSiteConfig get() {
        return repository.find().orElseThrow(JiraSiteNotConfiguredException::new);
    }

    public JiraSiteConfig updateBaseUrl(String baseUrl, String actor) {
        JiraSiteConfig config = new JiraSiteConfig(baseUrl, Instant.now(), actor);
        repository.save(config);
        return config;
    }

    /**
     * The seeded value comes from RM_JIRA_DEFAULT_BASE_URL, which never passes through the
     * REST DTO's validation — so without this check an operator's typo (a trailing slash is
     * the easy one) would be written to the database unvalidated and every outbound Jira call
     * would build a malformed URL. Failing at startup, like the credential-key length check,
     * is far cheaper to diagnose than a 404 from Jira later.
     */
    public void seedIfAbsent(String baseUrl) {
        if (!JIRA_CLOUD_URL.matcher(baseUrl).matches()) {
            throw new IllegalStateException(
                    "RM_JIRA_DEFAULT_BASE_URL must be of the form https://<site>.atlassian.net "
                            + "with no trailing slash; got: " + baseUrl);
        }
        if (repository.find().isEmpty()) {
            repository.save(new JiraSiteConfig(baseUrl, Instant.now(), SEED_ACTOR));
        }
    }

    public static class JiraSiteNotConfiguredException extends RuntimeException {
    }
}
