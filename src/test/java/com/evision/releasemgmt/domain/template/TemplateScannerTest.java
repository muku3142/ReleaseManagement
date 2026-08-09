package com.evision.releasemgmt.domain.template;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D-04-06's enforcement test: every construct outside the allowed Mustache subset is rejected with
// a specific, actionable message; unknown-but-legal-shape tokens are collected, not thrown.
class TemplateScannerTest {

    private final TemplateScanner scanner = new TemplateScanner();

    @Test
    void validVariableSectionInvertedSectionAndCommentScanClean() {
        String source = "{{release.name}}{{#items}}{{/items}}{{^items}}{{/items}}{{! note }}";
        ScanResult result = scanner.scan(source, "Body");
        assertThat(result.unknownTokens()).isEmpty();
    }

    @Test
    void partialThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{>header}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Partials are not supported: {{>header}}.");
    }

    @Test
    void tripleMustacheThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{{release.name}}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Unescaped variables ({{{ }}} and {{& }}) are not supported: "
                        + "{{{release.name}}}.");
    }

    @Test
    void ampersandUnescapedThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{&release.name}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Unescaped variables ({{{ }}} and {{& }}) are not supported: "
                        + "{{&release.name}}.");
    }

    @Test
    void customDelimitersThrowMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{=<% %>=}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Custom delimiters are not supported.");
    }

    @Test
    void unclosedSectionThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{#items}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Unclosed section: {{#items}} has no matching {{/items}}.");
    }

    @Test
    void unexpectedClosingTagThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{/items}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Unexpected closing tag: {{/items}}.");
    }

    @Test
    void mismatchedClosingTagThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{#items}}{{/groups}}", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Mismatched closing tag: {{#items}} is closed by {{/groups}}.");
    }

    @Test
    void unterminatedPlaceholderThrowsMalformed() {
        assertThatThrownBy(() -> scanner.scan("{{release.name", "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Unterminated placeholder: {{release.name");
    }

    @Test
    void unknownTokenAtRootScansCleanButIsReported() {
        ScanResult result = scanner.scan("{{ticket.compnent}}", "Body");
        assertThat(result.unknownTokens()).containsExactly("ticket.compnent");
    }

    @Test
    void statusAtRootIsUnknownButInsideItemsIsNot() {
        ScanResult rootResult = scanner.scan("{{status}}", "Body");
        assertThat(rootResult.unknownTokens()).containsExactly("status");

        ScanResult itemResult = scanner.scan("{{#items}}{{status}}{{/items}}", "Body");
        assertThat(itemResult.unknownTokens()).isEmpty();
    }

    @Test
    void rootTokenInsideItemsIsReportedAsUnknownBecauseScopesDoNotLeak() {
        ScanResult result = scanner.scan("{{#items}}{{release.name}}{{/items}}", "Body");
        assertThat(result.unknownTokens()).containsExactly("release.name");
    }

    @Test
    void nestedCommitsSectionInsideItemsScansCleanWithNoUnknowns() {
        ScanResult result = scanner.scan("{{#items}}{{#commits}}{{message}}{{/commits}}{{/items}}", "Body");
        assertThat(result.unknownTokens()).isEmpty();
    }

    @Test
    void booleanIsManualSectionScansCleanAndDoesNotChangeScope() {
        ScanResult result = scanner.scan("{{#items}}{{#isManual}}manual{{/isManual}}{{key}}{{/items}}", "Body");
        assertThat(result.unknownTokens()).isEmpty();
    }

    @Test
    void unknownSectionTokenStillBalancesWithoutThrowing() {
        ScanResult result = scanner.scan("{{#nope}}x{{/nope}}", "Body");
        assertThat(result.unknownTokens()).containsExactly("nope");
    }

    @Test
    void tooManyTagsThrowsMalformed() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 5001; i++) {
            source.append("{{release.name}}");
        }
        assertThatThrownBy(() -> scanner.scan(source.toString(), "Body"))
                .isInstanceOf(TemplateMalformedException.class)
                .hasMessage("Template has too many placeholders.");
    }

    @Test
    void unknownTokensAreDeduplicatedAndReportedInSourceOrder() {
        ScanResult result = scanner.scan("{{ticket.compnent}}{{status}}{{ticket.compnent}}", "Body");
        assertThat(result.unknownTokens()).containsExactly("ticket.compnent", "status");
    }

    @Test
    void nullAndEmptySourceScanCleanWithNoUnknowns() {
        assertThat(scanner.scan(null, "Body").unknownTokens()).isEmpty();
        assertThat(scanner.scan("", "Body").unknownTokens()).isEmpty();
    }
}
