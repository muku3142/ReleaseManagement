package com.evision.releasemgmt.domain.template;

import com.evision.releasemgmt.domain.template.view.CommentView;
import com.evision.releasemgmt.domain.template.view.CommitView;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.ItemView;
import com.evision.releasemgmt.domain.template.view.ProductView;
import com.evision.releasemgmt.domain.template.view.PullRequestView;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// D-04-20: the drift guard. If PlaceholderCatalog.ENTRIES falls out of sync with the view-model's
// actual record components, this test fails the build - the catalog can never silently lie about
// what a template can reach.
class PlaceholderCatalogTest {

    private static Set<String> names(RecordComponent[] components) {
        return java.util.Arrays.stream(components)
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> catalogTokens(PlaceholderScope scope) {
        return PlaceholderCatalog.ENTRIES.stream()
                .filter(e -> e.scope() == scope)
                .map(PlaceholderEntry::token)
                .collect(Collectors.toSet());
    }

    @Test
    void rootTokensMatchReleaseViewComponents() {
        Set<String> rootTokens = catalogTokens(PlaceholderScope.ROOT).stream()
                .map(t -> t.startsWith("release.") ? t.substring("release.".length()) : t)
                .collect(Collectors.toSet());
        assertThat(rootTokens).isEqualTo(names(ReleaseView.class.getRecordComponents()));
    }

    @Test
    void itemTokensMatchItemViewComponents() {
        assertThat(catalogTokens(PlaceholderScope.ITEM)).isEqualTo(names(ItemView.class.getRecordComponents()));
    }

    @Test
    void groupTokensMatchGroupViewComponents() {
        assertThat(catalogTokens(PlaceholderScope.GROUP)).isEqualTo(names(GroupView.class.getRecordComponents()));
    }

    @Test
    void productTokensMatchProductViewComponents() {
        assertThat(catalogTokens(PlaceholderScope.PRODUCT))
                .isEqualTo(names(ProductView.class.getRecordComponents()));
    }

    @Test
    void commentTokensMatchCommentViewComponents() {
        assertThat(catalogTokens(PlaceholderScope.COMMENT))
                .isEqualTo(names(CommentView.class.getRecordComponents()));
    }

    @Test
    void commitTokensMatchCommitViewComponents() {
        assertThat(catalogTokens(PlaceholderScope.COMMIT)).isEqualTo(names(CommitView.class.getRecordComponents()));
    }

    @Test
    void pullRequestTokensMatchPullRequestViewComponents() {
        assertThat(catalogTokens(PlaceholderScope.PULL_REQUEST))
                .isEqualTo(names(PullRequestView.class.getRecordComponents()));
    }

    @Test
    void sectionEntriesPushToTheScopeThatMapsToTheirListComponent() {
        for (PlaceholderEntry entry : PlaceholderCatalog.ENTRIES) {
            if (entry.kind() != PlaceholderKind.SECTION) {
                continue;
            }
            Class<?> owningType = viewTypeFor(entry.scope());
            RecordComponent component = componentFor(owningType, entry.token());
            if (component.getType().equals(boolean.class)) {
                assertThat(entry.pushes())
                        .as("boolean section %s.%s should keep the enclosing scope", entry.scope(), entry.token())
                        .isNull();
            } else {
                assertThat(entry.pushes())
                        .as("list section %s.%s must push to the scope mapping its element type",
                                entry.scope(), entry.token())
                        .isNotNull();
            }
        }
    }

    @Test
    void everyEntryHasAProperlyPunctuatedDescription() {
        for (PlaceholderEntry entry : PlaceholderCatalog.ENTRIES) {
            assertThat(entry.description())
                    .as("description for %s.%s", entry.scope(), entry.token())
                    .isNotBlank()
                    .endsWith(".");
        }
    }

    @Test
    void scopesDoNotLeak() {
        assertThat(PlaceholderCatalog.find(PlaceholderScope.ITEM, "release.name")).isEmpty();
        assertThat(PlaceholderCatalog.find(PlaceholderScope.ROOT, "status")).isEmpty();
    }

    @Test
    void noEntryIsTaggedAlwaysEmptyUntilPhaseSix() {
        assertThat(PlaceholderCatalog.ENTRIES)
                .allSatisfy(entry -> assertThat(entry.tags()).doesNotContain("Always empty until Phase 6"));
    }

    private static Class<?> viewTypeFor(PlaceholderScope scope) {
        return switch (scope) {
            case ROOT -> ReleaseView.class;
            case ITEM -> ItemView.class;
            case GROUP -> GroupView.class;
            case COMMENT -> CommentView.class;
            case COMMIT -> CommitView.class;
            case PULL_REQUEST -> PullRequestView.class;
            case PRODUCT -> ProductView.class;
        };
    }

    private static RecordComponent componentFor(Class<?> type, String token) {
        String name = token.startsWith("release.") ? token.substring("release.".length()) : token;
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        throw new AssertionError("No record component " + name + " on " + type);
    }
}
