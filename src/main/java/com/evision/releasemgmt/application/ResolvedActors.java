package com.evision.releasemgmt.application;

import java.util.Map;

/** Emails resolved for one response. */
public record ResolvedActors(Map<String, String> emailsByAccountId) {

    /**
     * D-05.3-04: an account id with no credential row displays as itself. The raw id stays traceable
     * in Jira, which is strictly more useful than a blank or an "Unknown" placeholder.
     */
    public String display(String accountId) {
        return accountId == null ? null : emailsByAccountId.getOrDefault(accountId, accountId);
    }
}
