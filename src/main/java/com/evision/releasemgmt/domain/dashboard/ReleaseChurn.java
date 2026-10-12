package com.evision.releasemgmt.domain.dashboard;

import com.evision.releasemgmt.domain.enrichment.CommitsSource;
import com.evision.releasemgmt.domain.enrichment.EnrichmentStatus;
import java.util.List;
import java.util.OptionalDouble;

// INSIGHT-03: this release's code and test churn from ADO commits and Jira status history - not Jira cycle time (D-08-05..07).
public record ReleaseChurn(int tickets, int linked, int noLink, int failed, int noData,
                           Double medianCommitsToDone, int commitNoData, long reopens, int reopenNoData,
                           Double postDoneShare, int keyTagged, List<TicketChurn> perTicket) {

    static final int MIN_FENCE_SAMPLE = 4;
    static final int OUTLIER_REOPENS = 2;
    private static final double FENCE_MULTIPLIER = 1.5;

    public static ReleaseChurn of(List<ChurnItem> items) {
        List<ChurnItem> withCommits = items.stream().filter(ChurnItem::hasCommitData).toList();
        List<Long> commitsToDone = withCommits.stream().map(ChurnItem::commitsToDone).toList();

        Double fence = null;
        if (commitsToDone.size() >= MIN_FENCE_SAMPLE) {
            double q1 = Stats.quantile(commitsToDone, 0.25).orElseThrow();
            double q3 = Stats.quantile(commitsToDone, 0.75).orElseThrow();
            fence = q3 + FENCE_MULTIPLIER * (q3 - q1);
        }

        long postDone = withCommits.stream().mapToLong(ChurnItem::postDoneCommits).sum();
        long total = withCommits.stream().mapToLong(ChurnItem::commitTotal).sum();
        List<ChurnItem> withReopens = items.stream().filter(ChurnItem::hasReopenData).toList();

        List<TicketChurn> perTicket = new java.util.ArrayList<>();
        for (ChurnItem item : items) {
            boolean commitData = item.hasCommitData();
            boolean reopenData = item.hasReopenData();
            boolean outlier = (commitData && fence != null && item.commitsToDone() > fence)
                    || (reopenData && item.reopenCount() >= OUTLIER_REOPENS);
            perTicket.add(new TicketChurn(item.ticketKey(),
                    commitData ? item.commitsToDone() : null,
                    commitData ? item.postDoneCommits() : null,
                    reopenData ? item.reopenCount() : null,
                    commitData && item.commitsSource() == CommitsSource.KEY_TAGGED,
                    outlier));
        }

        OptionalDouble median = Stats.median(commitsToDone);
        return new ReleaseChurn(
                items.size(),
                count(items, EnrichmentStatus.LINKED),
                count(items, EnrichmentStatus.NO_LINK),
                count(items, EnrichmentStatus.FAILED),
                count(items, EnrichmentStatus.NOT_ATTEMPTED),
                median.isPresent() ? median.getAsDouble() : null,
                items.size() - withCommits.size(),
                withReopens.stream().mapToLong(ChurnItem::reopenCount).sum(),
                items.size() - withReopens.size(),
                total > 0 ? (double) postDone / total : null,
                (int) withCommits.stream().filter(i -> i.commitsSource() == CommitsSource.KEY_TAGGED).count(),
                List.copyOf(perTicket));
    }

    public int outliers() {
        return (int) perTicket.stream().filter(TicketChurn::outlier).count();
    }

    // D-08-04: LINKED counts as linked whether or not enrichment is incomplete.
    private static int count(List<ChurnItem> items, EnrichmentStatus status) {
        return (int) items.stream().filter(i -> i.enrichmentStatus() == status).count();
    }
}
