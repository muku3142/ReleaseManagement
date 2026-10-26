package com.evision.releasemgmt.domain.roadmap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class RoadmapRanking {

    private static final Comparator<RoadmapItem> OLDEST_FIRST =
            Comparator.comparing(RoadmapItem::createdAt).thenComparing(RoadmapItem::id);

    private static final Comparator<ScoredItem> SCORED_ORDER = Comparator
            .comparing(ScoredItem::score, Comparator.reverseOrder())
            .thenComparing(s -> s.item().inputs().impact(), Comparator.reverseOrder())
            .thenComparing(ScoredItem::item, OLDEST_FIRST);

    private RoadmapRanking() {}

    // The one ordering shared by the list, the CSV and the Phase 20 planner.
    public static List<RankedItem> rank(Collection<RoadmapItem> items) {
        List<ScoredItem> scored = new ArrayList<>();
        List<RoadmapItem> unscored = new ArrayList<>();
        for (RoadmapItem item : items) {
            RiceScorer.score(item.inputs()).ifPresentOrElse(
                    score -> scored.add(new ScoredItem(item, score)), () -> unscored.add(item));
        }
        scored.sort(SCORED_ORDER);
        unscored.sort(OLDEST_FIRST);

        List<RankedItem> ranked = new ArrayList<>(items.size());
        for (int i = 0; i < scored.size(); i++) {
            ranked.add(new RankedItem(scored.get(i).item(), scored.get(i).score(), i + 1));
        }
        unscored.forEach(item -> ranked.add(new RankedItem(item, null, null)));
        return ranked;
    }

    private record ScoredItem(RoadmapItem item, BigDecimal score) {}
}
