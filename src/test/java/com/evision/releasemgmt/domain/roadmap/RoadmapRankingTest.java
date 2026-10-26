package com.evision.releasemgmt.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoadmapRankingTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static RiceInputs inputs(int reach, String impact, int confidence, String effort) {
        return RiceInputs.validated(
                BigDecimal.valueOf(reach), new BigDecimal(impact), BigDecimal.valueOf(confidence), new BigDecimal(effort));
    }

    private static RoadmapItem item(UUID id, RiceInputs inputs, Instant createdAt, UUID productId, RoadmapStatus status,
                                    Instant archivedAt) {
        return new RoadmapItem(id, RoadmapItemSource.MANUAL, null, null, "t", null, null, null, null, productId, status,
                null, null, null, inputs, createdAt, "a", archivedAt);
    }

    private static RoadmapItem item(UUID id, RiceInputs inputs, Instant createdAt) {
        return item(id, inputs, createdAt, null, RoadmapStatus.IDEA, null);
    }

    private static UUID uuid(long n) {
        return new UUID(0, n);
    }

    @Test
    void scoredFirstThenUnscoredWithRanks() {
        RoadmapItem unscoredOld = item(uuid(1), RiceInputs.NONE, T0);
        RoadmapItem five = item(uuid(2), inputs(5, "1", 100, "1"), T0);
        RoadmapItem ten = item(uuid(3), inputs(10, "1", 100, "1"), T0);
        RoadmapItem unscoredNew = item(uuid(4), RiceInputs.NONE, T0.plusSeconds(10));

        List<RankedItem> ranked = RoadmapRanking.rank(List.of(unscoredOld, five, ten, unscoredNew));

        assertThat(ranked).extracting(r -> r.item().id()).containsExactly(uuid(3), uuid(2), uuid(1), uuid(4));
        assertThat(ranked).extracting(RankedItem::rank).containsExactly(1, 2, null, null);
        assertThat(ranked.get(0).score()).isEqualByComparingTo("10");
        assertThat(ranked.get(2).score()).isNull();
    }

    @Test
    void tiesBreakByImpactThenCreatedAtThenId() {
        // all score 2
        RoadmapItem highImpact = item(uuid(9), inputs(4, "2", 100, "4"), T0.plusSeconds(99));
        RoadmapItem lowImpactEarly = item(uuid(8), inputs(2, "1", 100, "1"), T0);
        RoadmapItem lowImpactLate = item(uuid(7), inputs(2, "1", 100, "1"), T0.plusSeconds(5));
        RoadmapItem sameTimeSmallId = item(uuid(5), inputs(2, "1", 100, "1"), T0);

        List<RankedItem> ranked =
                RoadmapRanking.rank(List.of(lowImpactLate, highImpact, lowImpactEarly, sameTimeSmallId));

        assertThat(ranked).extracting(r -> r.item().id())
                .containsExactly(uuid(9), uuid(5), uuid(8), uuid(7));
    }

    @Test
    void stableUnderShuffledInput() {
        List<RoadmapItem> items = new ArrayList<>();
        for (int n = 1; n <= 12; n++) {
            RiceInputs in = n % 3 == 0 ? RiceInputs.NONE : inputs(n % 4 + 1, "1", 100, "1");
            items.add(item(uuid(n), in, T0.plusSeconds(n % 5)));
        }
        List<UUID> expected = RoadmapRanking.rank(items).stream().map(r -> r.item().id()).toList();
        Random random = new Random(42);
        for (int i = 0; i < 20; i++) {
            List<RoadmapItem> shuffled = new ArrayList<>(items);
            Collections.shuffle(shuffled, random);
            assertThat(RoadmapRanking.rank(shuffled).stream().map(r -> r.item().id()).toList()).isEqualTo(expected);
        }
    }

    @Test
    void filterExcludesDoneAndArchivedByDefault() {
        RoadmapItem open = item(uuid(1), RiceInputs.NONE, T0);
        RoadmapItem done = item(uuid(2), RiceInputs.NONE, T0, null, RoadmapStatus.DONE, null);
        RoadmapItem archived = item(uuid(3), RiceInputs.NONE, T0, null, RoadmapStatus.IDEA, T0);

        assertThat(new RoadmapFilter(Set.of(), false, false).matches(open)).isTrue();
        assertThat(new RoadmapFilter(Set.of(), false, false).matches(done)).isFalse();
        assertThat(new RoadmapFilter(Set.of(), false, false).matches(archived)).isFalse();
        assertThat(new RoadmapFilter(Set.of(), true, false).matches(done)).isTrue();
        assertThat(new RoadmapFilter(Set.of(), false, true).matches(archived)).isTrue();
    }

    @Test
    void filterByProductExcludesItemsWithoutOrOtherProduct() {
        UUID p = uuid(100);
        RoadmapItem mine = item(uuid(1), RiceInputs.NONE, T0, p, RoadmapStatus.IDEA, null);
        RoadmapItem other = item(uuid(2), RiceInputs.NONE, T0, uuid(101), RoadmapStatus.IDEA, null);
        RoadmapItem none = item(uuid(3), RiceInputs.NONE, T0);

        RoadmapFilter filter = new RoadmapFilter(Set.of(p), true, true);

        assertThat(filter.matches(mine)).isTrue();
        assertThat(filter.matches(other)).isFalse();
        assertThat(filter.matches(none)).isFalse();
        assertThat(new RoadmapFilter(Set.of(), false, false).matches(none)).isTrue();
    }
}
