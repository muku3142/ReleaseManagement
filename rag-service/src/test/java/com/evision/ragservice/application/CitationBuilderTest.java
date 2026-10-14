package com.evision.ragservice.application;

import com.evision.ragservice.application.CitationBuilder.Result;
import com.evision.ragservice.domain.Citation;
import com.evision.ragservice.domain.CitationBasis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationBuilderTest {

    private final CitationBuilder builder = new CitationBuilder(0.55);
    private final List<SourceDocument> sent = List.of(doc(1, 0.9), doc(2, 0.7), doc(3, 0.6), doc(4, 0.5));

    private static SourceDocument doc(int n, double score) {
        return new SourceDocument(n, "doc-" + n, "Title " + n, "http://x/" + n, "col", Map.of("k", n),
                List.of("p"), score);
    }

    private static List<Integer> ns(Result r) {
        return r.items().stream().map(Citation::n).toList();
    }

    @Test
    void markersGiveMarkedSortedAndCopyMetadata() {
        Result r = builder.build("A [2]. B [1].", sent);

        assertThat(r.basis()).isEqualTo(CitationBasis.MARKED);
        assertThat(ns(r)).containsExactly(1, 2);
        assertThat(r.items().get(0)).isEqualTo(new Citation(1, "doc-1", "Title 1", "http://x/1", "col", Map.of("k", 1)));
        assertThat(r.inventedIds()).isZero();
    }

    @Test
    void duplicatesAndGroupedMarkersCollapse() {
        Result r = builder.build("[1][1] and [1, 3]", sent);

        assertThat(r.basis()).isEqualTo(CitationBasis.MARKED);
        assertThat(ns(r)).containsExactly(1, 3);
    }

    @Test
    void inventedIdsAreDroppedAndCounted() {
        Result r = builder.build("See [9] and [2]", sent);

        assertThat(r.basis()).isEqualTo(CitationBasis.MARKED);
        assertThat(ns(r)).containsExactly(2);
        assertThat(r.inventedIds()).isEqualTo(1);
    }

    @Test
    void onlyInvalidMarkersFallBackToRetrievedAtOrAboveMinScore() {
        Result r = builder.build("See [9]", sent);

        assertThat(r.basis()).isEqualTo(CitationBasis.RETRIEVED);
        assertThat(ns(r)).containsExactly(1, 2, 3);
        assertThat(r.inventedIds()).isEqualTo(1);
    }

    @Test
    void retrievedIsCappedAtThree() {
        List<SourceDocument> many = List.of(doc(1, 0.9), doc(2, 0.9), doc(3, 0.9), doc(4, 0.9));

        assertThat(ns(builder.build("no markers", many))).containsExactly(1, 2, 3);
    }

    @Test
    void noMarkersAndAllBelowMinScoreGivesEmptyRetrieved() {
        Result r = builder.build("no markers", List.of(doc(1, 0.3), doc(2, 0.2)));

        assertThat(r.basis()).isEqualTo(CitationBasis.RETRIEVED);
        assertThat(r.items()).isEmpty();
    }

    @Test
    void urlOrIdInAnswerTextNeverAddsACitation() {
        Result r = builder.build("see http://evil/x and doc-99", List.of(doc(1, 0.1)));

        assertThat(r.items()).isEmpty();
    }
}
