package com.evision.releasemgmt.domain.jira;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TicketSearchCriteriaTest {

    @Test
    void composesIdOnlyJqlWithDoneOnlyDefaultWhenCategoriesOmitted() {
        assertThat(TicketSearchCriteria.byProject("10023", "10891", null).toJql())
                .isEqualTo("project = 10023 AND fixVersion = 10891 AND statusCategory IN (\"Done\") ORDER BY key ASC");
    }

    @Test
    void forwardsRawJqlUnchangedWithNoInjectedStatusClause() {
        String raw = "project = CNV AND status = Done";

        String composed = TicketSearchCriteria.byJql(raw).toJql();

        assertThat(composed).isEqualTo(raw);
        assertThat(composed).doesNotContain("ORDER BY");
        assertThat(composed).doesNotContain("statusCategory");
    }

    @Test
    void jqlModeLeavesPickerFieldsNull() {
        TicketSearchCriteria criteria = TicketSearchCriteria.byJql("project = CNV");

        assertThat(criteria.projectId()).isNull();
        assertThat(criteria.fixVersionId()).isNull();
    }

    @Test
    void composesExplicitlyRequestedStatusCategories() {
        String composed = TicketSearchCriteria
                .byProject("10023", "10891", EnumSet.of(StatusCategory.TO_DO, StatusCategory.IN_PROGRESS))
                .toJql();

        assertThat(composed).contains("statusCategory IN (\"To Do\", \"In Progress\")");
        assertThat(composed).doesNotContain("\"Done\"");
    }

    @Test
    void emptyCategorySetMeansEveryStatusSoNoClauseIsComposed() {
        String composed = TicketSearchCriteria.byProject("10023", "10891", Set.of()).toJql();

        assertThat(composed).doesNotContain("statusCategory");
        assertThat(composed).isEqualTo("project = 10023 AND fixVersion = 10891 ORDER BY key ASC");
    }

    @Test
    void omitsFixVersionClauseWhenNoVersionIsSelected() {
        String composed = TicketSearchCriteria.byProject("10023", null, null).toJql();

        assertThat(composed).doesNotContain("fixVersion");
        assertThat(composed)
                .isEqualTo("project = 10023 AND statusCategory IN (\"Done\") ORDER BY key ASC");
    }

    @Test
    void treatsBlankFixVersionAsAbsentRatherThanMatchingOnEmpty() {
        String composed = TicketSearchCriteria.byProject("10023", "   ", null).toJql();

        assertThat(composed).doesNotContain("fixVersion");
    }

    @Test
    void projectOnlyWithEveryStatusComposesTheBroadestQuery() {
        String composed = TicketSearchCriteria.byProject("10023", null, Set.of()).toJql();

        assertThat(composed).isEqualTo("project = 10023 ORDER BY key ASC");
    }

    @Test
    void statusClauseAlwaysPrecedesOrderBy() {
        String composed = TicketSearchCriteria
                .byProject("10023", "10891", EnumSet.allOf(StatusCategory.class))
                .toJql();

        assertThat(composed).endsWith("ORDER BY key ASC");
        assertThat(composed.indexOf("statusCategory")).isLessThan(composed.indexOf("ORDER BY"));
    }
}
