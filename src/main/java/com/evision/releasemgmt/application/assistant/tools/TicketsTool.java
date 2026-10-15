package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.dashboard.TicketChurn;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class TicketsTool implements AssistantTool {

    static final int MAX_TICKETS = 50;
    static final int MAX_RELEASES = 25;
    static final int KEY_MAX_LENGTH = 40;
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    public record Churn(Long commitsToDone, Long postDoneCommits, Integer reopens, boolean outlier) {
    }

    public record TicketRow(String releaseName, String releaseVersion, String key, String summary, String issueType,
                            String status, StatusCategory statusCategory, String resolution, String assignee,
                            String reporter, List<String> fixVersions, String component, BigDecimal storyPoints,
                            int commentTotal, Instant updatedAt, int prCount, int commitCount,
                            EnrichmentStatus enrichmentStatus, Churn churn) {
    }

    public record TicketList(List<TicketRow> tickets, boolean truncated) {
    }

    private final ReleaseService releaseService;
    private final DashboardService dashboardService;
    private final ReleaseRef releaseRef;

    public TicketsTool(ReleaseService releaseService, DashboardService dashboardService, ReleaseRef releaseRef) {
        this.releaseService = releaseService;
        this.dashboardService = dashboardService;
        this.releaseRef = releaseRef;
    }

    @Override
    public String name() {
        return "tickets";
    }

    @Override
    public String description() {
        return "Ticket facts and churn: by `key` across releases, or by `release` (outliers first).";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "release", Map.of("type", "string", "maxLength", ReleaseRef.MAX_LENGTH),
                        "key", Map.of("type", "string", "maxLength", KEY_MAX_LENGTH)),
                "additionalProperties", false);
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        Object key = arguments.get("key");
        Object release = arguments.get("release");
        if (key != null) {
            String ticketKey = key.toString().trim();
            if (ticketKey.length() > KEY_MAX_LENGTH || !KEY.matcher(ticketKey).matches()) {
                throw new ToolArgumentException("key must look like PAY-12");
            }
            return new ToolOutput(byKey(ticketKey), "tickets:" + ticketKey, "ReleaseManagement: ticket " + ticketKey);
        }
        if (release == null) {
            throw new ToolArgumentException("provide key or release");
        }
        UUID id = releaseRef.resolve(release);
        Release found = releaseService.get(id);
        return new ToolOutput(byRelease(found), "tickets:" + id,
                "ReleaseManagement: tickets of " + found.name() + " " + found.version());
    }

    private TicketList byKey(String ticketKey) {
        List<ReleaseSummary> releases = releaseService.releasesContaining(ticketKey);
        List<TicketRow> rows = releases.stream().limit(MAX_RELEASES).flatMap(summary -> {
            Release found = releaseService.get(summary.id());
            Map<String, TicketChurn> churn = churnByKey(found.id());
            return found.items().stream().filter(item -> item.ticketKey().equals(ticketKey))
                    .map(item -> row(found, item, churn.get(ticketKey)));
        }).toList();
        return new TicketList(rows, releases.size() > MAX_RELEASES);
    }

    private TicketList byRelease(Release found) {
        Map<String, TicketChurn> churn = churnByKey(found.id());
        List<TicketRow> all = found.items().stream().map(item -> row(found, item, churn.get(item.ticketKey())))
                .sorted(Comparator.comparing((TicketRow r) -> r.churn() == null || !r.churn().outlier())
                        .thenComparing(TicketRow::key))
                .toList();
        return new TicketList(all.stream().limit(MAX_TICKETS).toList(), all.size() > MAX_TICKETS);
    }

    private Map<String, TicketChurn> churnByKey(UUID releaseId) {
        return dashboardService.ticketChurn(releaseId).stream()
                .collect(Collectors.toMap(TicketChurn::ticketKey, Function.identity(), (a, b) -> a));
    }

    private static TicketRow row(Release release, ReleaseItem item, TicketChurn churn) {
        TicketDetail t = item.ticket();
        CodeLinks links = item.codeLinks();
        return new TicketRow(release.name(), release.version(), t.key(), t.summary(), t.issueType(), t.status(),
                t.statusCategory(), t.resolution(), t.assignee(), t.reporter(), t.fixVersions(), t.component(),
                t.storyPointsRead() ? t.storyPoints() : null, t.commentTotal(), t.updatedAt(),
                links.pullRequests().size(), links.commits().size(), links.status(),
                churn == null ? null : new Churn(churn.commitsToDone(), churn.postDoneCommits(), churn.reopens(),
                        churn.outlier()));
    }
}
