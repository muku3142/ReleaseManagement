package com.evision.releasemgmt.domain.roadmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoadmapRanking {

    private static final Comparator<RoadmapItem> OLDEST_FIRST =
            Comparator.comparing(RoadmapItem::createdAt).thenComparing(RoadmapItem::id);

    private static final Comparator<RankedItem> SCORED_ORDER = Comparator
            .comparing(RankedItem::score, Comparator.reverseOrder())
            .thenComparing(r -> r.effective().impact(), Comparator.reverseOrder())
            .thenComparing(RankedItem::item, OLDEST_FIRST);

    private RoadmapRanking() {}

    // The one ordering shared by the list, the CSV and the Phase 20 planner.
    public static List<RankedItem> rank(Collection<RoadmapItem> items, Map<UUID, Integer> runningClientsByProduct) {
        List<RankedItem> scored = new ArrayList<>();
        List<RankedItem> unscored = new ArrayList<>();
        for (RoadmapItem item : items) {
            RankedItem unranked = unranked(item, runningClientsByProduct.getOrDefault(item.productId(), 0));
            (unranked.score() == null ? unscored : scored).add(unranked);
        }
        scored.sort(SCORED_ORDER);
        unscored.sort(Comparator.comparing(RankedItem::item, OLDEST_FIRST));

        List<RankedItem> ranked = new ArrayList<>(items.size());
        for (int i = 0; i < scored.size(); i++) {
            RankedItem s = scored.get(i);
            ranked.add(new RankedItem(s.item(), s.calculated(), s.effective(), s.score(), i + 1));
        }
        ranked.addAll(unscored);
        return ranked;
    }

    public static RankedItem unranked(RoadmapItem item, int runningClients) {
        RiceInputs calculated = RiceCalculation.calculated(item, runningClients);
        RiceInputs effective = RiceCalculation.effective(item, calculated);
        return new RankedItem(item, calculated, effective, RiceScorer.score(effective).orElse(null), null);
    }
}
