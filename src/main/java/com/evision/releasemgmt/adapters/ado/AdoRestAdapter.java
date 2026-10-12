package com.evision.releasemgmt.adapters.ado;

import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoCredential;
import com.evision.releasemgmt.domain.ado.AdoFailureReason;
import com.evision.releasemgmt.domain.enrichment.AdoRepoRef;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.ports.AdoRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Component
public class AdoRestAdapter implements AdoRepositoryPort {

    private static final int NON_AUTHORITATIVE = 203;
    private static final Pattern GUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern NUMERIC_ID = Pattern.compile("^[0-9]{1,10}$");
    private static final String CONTINUATION_HEADER = "x-ms-continuationtoken";
    private static final String COMMITS_PATH =
            "/{organization}/{project}/_apis/git/repositories/{repository}/pullRequests/{pullRequest}/commits"
                    + "?$top=100&api-version=7.1";
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    private final RestClient adoRestClient;
    private final String baseUrl;
    private final int maxPagesPerPr;
    // Adapter-wide on purpose: one shared PAT is one shared 200 TSTU / 5 min budget across every user and worker.
    private final AtomicReference<Instant> notBefore = new AtomicReference<>(Instant.EPOCH);

    // baseUrl is configuration (tests point it at WireMock), never request input; production is https://dev.azure.com.
    public AdoRestAdapter(RestClient adoRestClient, @Value("${app.ado.base-url}") String baseUrl,
                          @Value("${app.ado.max-pages-per-pr}") int maxPagesPerPr) {
        this.adoRestClient = adoRestClient;
        this.baseUrl = baseUrl;
        this.maxPagesPerPr = maxPagesPerPr;
    }

    @Override
    public void validate(String organization, String token) {
        try {
            retrieve(baseUrl + "/{organization}/_apis/git/repositories?api-version=7.1", token, organization)
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public List<LinkedCommit> pullRequestCommits(AdoCredential credential, AdoRepoRef repository, String repositoryId,
                                                 String pullRequestId) {
        if (repositoryId == null || !GUID.matcher(repositoryId).matches()
                || pullRequestId == null || !NUMERIC_ID.matcher(pullRequestId).matches()) {
            throw new IllegalArgumentException("Invalid repository or pull request identifier");
        }
        Map<String, LinkedCommit> bySha = new LinkedHashMap<>();
        String continuation = null;
        int pages = 0;
        do {
            if (++pages > maxPagesPerPr) {
                throw new AdoAccessException(AdoFailureReason.TOO_LARGE);
            }
            ResponseEntity<JsonNode> response = getPage(credential, repository, repositoryId, pullRequestId, continuation);
            JsonNode body = response.getBody();
            if (body != null) {
                body.path("value").forEach(commit -> addCommit(bySha, commit, repository));
            }
            continuation = response.getHeaders().getFirst(CONTINUATION_HEADER);
        } while (continuation != null && !continuation.isBlank());
        return List.copyOf(bySha.values());
    }

    private ResponseEntity<JsonNode> getPage(AdoCredential credential, AdoRepoRef repository, String repositoryId,
                                             String pullRequestId, String continuation) {
        String template = baseUrl + COMMITS_PATH + (continuation == null ? "" : "&continuationToken={continuation}");
        Object[] variables = continuation == null
                ? new Object[]{repository.organization(), repository.project(), repositoryId, pullRequestId}
                : new Object[]{repository.organization(), repository.project(), repositoryId, pullRequestId, continuation};
        try {
            awaitGate();
            try {
                return fetch(template, credential.token(), variables);
            } catch (HttpClientErrorException.TooManyRequests first) {
                closeGate(first.getResponseHeaders());
                awaitGate();
                return fetch(template, credential.token(), variables);
            }
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    private ResponseEntity<JsonNode> fetch(String template, String token, Object[] variables) {
        ResponseEntity<JsonNode> response = retrieve(template, token, variables).toEntity(JsonNode.class);
        closeGate(response.getHeaders());
        return response;
    }

    private RestClient.ResponseSpec retrieve(String template, String token, Object... variables) {
        return adoRestClient.get()
                .uri(template, variables)
                .header(HttpHeaders.AUTHORIZATION, basic(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(s -> s.is3xxRedirection() || s.value() == NON_AUTHORITATIVE
                                || s.value() == 401 || s.value() == 403,
                        (req, res) -> {
                            throw new AdoAccessException(AdoFailureReason.UNAUTHORIZED);
                        });
    }

    private static void addCommit(Map<String, LinkedCommit> bySha, JsonNode commit, AdoRepoRef repository) {
        String sha = commit.path("commitId").asString("");
        if (sha.isBlank()) {
            return;
        }
        Instant authoredAt;
        try {
            authoredAt = Instant.parse(commit.path("author").path("date").asString(""));
        } catch (DateTimeParseException e) {
            return;
        }
        bySha.putIfAbsent(sha, new LinkedCommit(sha, commit.path("comment").asString(""),
                commit.path("author").path("name").asString(null), authoredAt,
                repository.repositoryWebUrl() + "/commit/" + sha));
    }

    private void awaitGate() {
        long waitMillis = Duration.between(Instant.now(), notBefore.get()).toMillis();
        if (waitMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdoAccessException(AdoFailureReason.UNREACHABLE);
        }
    }

    // ADO may delay with a 200 plus Retry-After, so every response is observed, not only 429s.
    private void closeGate(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return;
        }
        Duration delay;
        try {
            delay = Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            delay = DEFAULT_RETRY_AFTER;
        }
        Instant until = Instant.now().plus(delay.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : delay);
        notBefore.accumulateAndGet(until, (current, candidate) -> candidate.isAfter(current) ? candidate : current);
    }

    private static AdoAccessException translate(RuntimeException e) {
        return switch (e) {
            case AdoAccessException ado -> ado;
            case HttpClientErrorException.NotFound ignored -> new AdoAccessException(AdoFailureReason.NOT_FOUND);
            case HttpClientErrorException.TooManyRequests ignored -> new AdoAccessException(AdoFailureReason.RATE_LIMITED);
            case HttpServerErrorException ignored -> new AdoAccessException(AdoFailureReason.ERROR);
            case ResourceAccessException ignored -> new AdoAccessException(AdoFailureReason.UNREACHABLE);
            case RestClientException ignored -> new AdoAccessException(AdoFailureReason.ERROR);
            default -> throw e;
        };
    }

    private static String basic(String token) {
        return "Basic " + Base64.getEncoder().encodeToString((":" + token).getBytes(StandardCharsets.UTF_8));
    }
}
