package com.evision.releasemgmt.domain.enrichment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketBranchRuleTest {

    @Test
    void matchesKeyAsWholeTokenCaseInsensitively() {
        assertThat(TicketBranchRule.isTicketBranch("feature/EXAMPLE-1", "EXAMPLE-1")).isTrue();
        assertThat(TicketBranchRule.isTicketBranch("example-1-fix-totals", "EXAMPLE-1")).isTrue();
    }

    @Test
    void rejectsLongerKeysPrefixedKeysEnvironmentBranchesAndNull() {
        assertThat(TicketBranchRule.isTicketBranch("EXAMPLE-12", "EXAMPLE-1")).isFalse();
        assertThat(TicketBranchRule.isTicketBranch("XEXAMPLE-1", "EXAMPLE-1")).isFalse();
        assertThat(TicketBranchRule.isTicketBranch("develop", "EXAMPLE-1")).isFalse();
        assertThat(TicketBranchRule.isTicketBranch(null, "EXAMPLE-1")).isFalse();
    }
}
