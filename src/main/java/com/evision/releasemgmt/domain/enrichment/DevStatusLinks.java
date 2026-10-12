package com.evision.releasemgmt.domain.enrichment;

import java.util.List;

public record DevStatusLinks(List<LinkedPullRequest> pullRequests, List<LinkedCommit> keyTaggedCommits,
                             List<String> branches) {

    public DevStatusLinks {
        pullRequests = pullRequests == null ? List.of() : List.copyOf(pullRequests);
        keyTaggedCommits = keyTaggedCommits == null ? List.of() : List.copyOf(keyTaggedCommits);
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    public static DevStatusLinks none() {
        return new DevStatusLinks(List.of(), List.of(), List.of());
    }

    // A branch alone is not a displayable link.
    public boolean hasDisplayableLinks() {
        return !pullRequests.isEmpty() || !keyTaggedCommits.isEmpty();
    }
}
