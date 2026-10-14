package com.evision.ragservice.application;

import com.evision.ragservice.domain.Citation;
import com.evision.ragservice.domain.CitationBasis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationBuilder {

    public static final int MAX_RETRIEVED = 3;
    private static final Pattern MARKER = Pattern.compile("\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)\\]");

    public record Result(CitationBasis basis, List<Citation> items, int inventedIds) {
    }

    private final double minScore;

    public CitationBuilder(double minScore) {
        this.minScore = minScore;
    }

    public Result build(String answer, List<SourceDocument> sent) {
        Set<Integer> marked = new TreeSet<>();
        Matcher m = MARKER.matcher(answer);
        while (m.find()) {
            for (String id : m.group(1).split(",")) {
                marked.add(Integer.parseInt(id.trim()));
            }
        }

        List<Citation> items = new ArrayList<>();
        int invented = 0;
        for (int id : marked) {
            SourceDocument doc = sent.stream().filter(d -> d.n() == id).findFirst().orElse(null);
            if (doc == null) {
                invented++;
            } else {
                items.add(citation(doc));
            }
        }
        if (!items.isEmpty()) {
            return new Result(CitationBasis.MARKED, items, invented);
        }

        List<Citation> retrieved = sent.stream()
                .filter(d -> d.topScore() >= minScore)
                .limit(MAX_RETRIEVED)
                .map(CitationBuilder::citation)
                .toList();
        return new Result(CitationBasis.RETRIEVED, retrieved, invented);
    }

    private static Citation citation(SourceDocument doc) {
        return new Citation(doc.n(), doc.documentId(), doc.title(), doc.url(), doc.collection(), doc.metadata());
    }
}
