package com.evision.releasemgmt.domain.enrichment;

import java.util.regex.Pattern;

// User-confirmed rule: the ticket key must appear in the source branch as a whole token; environment-merge PRs never count.
public final class TicketBranchRule {

    private TicketBranchRule() {
    }

    public static boolean isTicketBranch(String sourceBranch, String ticketKey) {
        if (sourceBranch == null || ticketKey == null) {
            return false;
        }
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(ticketKey) + "(?![0-9])", Pattern.CASE_INSENSITIVE)
                .matcher(sourceBranch).find();
    }
}
