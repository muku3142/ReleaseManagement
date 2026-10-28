package com.evision.releasemgmt.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
                null, null, null, inputs, RiceOverrides.NONE, null, null, createdAt, "a", archivedAt);
    }

    private static RoadmapItem item(UUID id, RiceInputs inputs, Instant createdAt) {
        return item(id, inputs, createdAt, uuid(1000), RoadmapStatus.IDEA, null);
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

        List<RankedItem> ranked = RoadmapRanking.rank(List.of(unscoredOld, five, ten, unscoredNew), Map.of());

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
                RoadmapRanking.rank(List.of(lowImpactLate, highImpact, lowImpactEarly, sameTimeSmallId), Map.of());

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
        List<UUID> expected = RoadmapRanking.rank(items, Map.of()).stream().map(r -> r.item().id()).toList();
        Random random = new Random(42);
        for (int i = 0; i < 20; i++) {
            List<RoadmapItem> shuffled = new ArrayList<>(items);
            Collections.shuffle(shuffled, random);
            assertThat(RoadmapRanking.rank(shuffled, Map.of()).stream().map(r -> r.item().id()).toList())
                    .isEqualTo(expected);
        }
    }

    private static RiceInputs entered(String impact, int confidence, String effort) {
        return RiceInputs.validated(null, new BigDecimal(impact), BigDecimal.valueOf(confidence), new BigDecimal(effort));
    }

    @Test
    void scoresWithCalculatedReachPerProductAndZeroForProductsNobodyRuns() {
        UUID productA = uuid(100);
        UUID productB = uuid(200);
        RoadmapItem onA = item(uuid(1), entered("1", 100, "1"), T0, productA, RoadmapStatus.IDEA, null);
        RoadmapItem onB = item(uuid(2), entered("1", 100, "1"), T0, productB, RoadmapStatus.IDEA, null);

        List<RankedItem> ranked = RoadmapRanking.rank(List.of(onB, onA), Map.of(productA, 10));

        assertThat(ranked).extracting(r -> r.item().id()).containsExactly(uuid(1), uuid(2));
        assertThat(ranked.get(0).score()).isEqualByComparingTo("10");
        assertThat(ranked.get(0).calculated().reach()).isEqualTo(10);
        assertThat(ranked.get(1).effective().reach()).isZero();
        assertThat(ranked.get(1).score()).isEqualByComparingTo("0");
        assertThat(ranked).extracting(RankedItem::rank).containsExactly(1, 2);
    }

    @Test
    void enteredReachWinsOverCalculatedReach() {
        UUID product = uuid(100);
        RoadmapItem overridden = item(uuid(1), inputs(20, "1", 100, "1"), T0, product, RoadmapStatus.IDEA, null);

        RankedItem ranked = RoadmapRanking.rank(List.of(overridden), Map.of(product, 7)).get(0);

        assertThat(ranked.calculated().reach()).isEqualTo(7);
        assertThat(ranked.effective().reach()).isEqualTo(20);
        assertThat(ranked.score()).isEqualByComparingTo("20");
    }

    @Test
    void tiesBreakOnEffectiveImpactOfJiraItems() {
        UUID product = uuid(100);
        RoadmapItem base = RoadmapItem.fromJira(
                new JiraSnapshot("K-1", "1", "t", "Open", T0, T0, "High", 3), product, "a", T0);
        // calculated impact 2, confidence 100; reach 4 (map); effort 4 -> score 2
        RoadmapItem jira = new RoadmapItem(uuid(9), base.source(), "K-1", "1", "t", "Open", T0, T0, null, product,
                RoadmapStatus.IDEA, null, null, null, new RiceInputs(null, null, null, new BigDecimal("4")),
                RiceOverrides.NONE, "High", 3, T0.plusSeconds(99), "a", null);
        RoadmapItem manualLowImpact = item(uuid(1), inputs(2, "1", 100, "1"), T0, product, RoadmapStatus.IDEA, null);

        List<RankedItem> ranked = RoadmapRanking.rank(List.of(manualLowImpact, jira), Map.of(product, 4));

        assertThat(ranked).extracting(r -> r.item().id()).containsExactly(uuid(9), uuid(1));
        assertThat(ranked.get(0).effective().impact()).isEqualByComparingTo("2");
    }
}
