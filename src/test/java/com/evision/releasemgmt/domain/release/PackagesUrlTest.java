package com.evision.releasemgmt.domain.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PackagesUrlTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankClearsTheLink(String raw) {
        assertThat(PackagesUrl.normalize(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://contoso.sharepoint.com/sites/x/Shared%20Documents/1.0",
            "HTTPS://Contoso.SharePoint.com/x",
            "https://onedrive.live.com/?id=abc",
            "https://1drv.ms/f/s!abc",
            "https://contoso.sharepoint.com:443/x"})
    void acceptsOneDriveAndSharePointHosts(String url) {
        assertThat(PackagesUrl.normalize(url)).isEqualTo(url);
    }

    @Test
    void returnsTheTrimmedLink() {
        assertThat(PackagesUrl.normalize("  https://1drv.ms/f/abc  ")).isEqualTo("https://1drv.ms/f/abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://contoso.sharepoint.com/x",
            "https://sharepoint.com/x",
            "https://evil.com/.sharepoint.com",
            "https://contoso.sharepoint.com.evil.com/x",
            "https://user@contoso.sharepoint.com/x",
            "https://evil.com\\@contoso.sharepoint.com",
            "javascript:alert(1)",
            "https://contoso.sharepoint.com:8443/x",
            "not a url",
            "ftp://1drv.ms/x"})
    void rejectsEverythingElse(String url) {
        assertThatThrownBy(() -> PackagesUrl.normalize(url)).isInstanceOf(InvalidPackagesUrlException.class);
    }

    @Test
    void rejectsOverlongLinks() {
        String url = "https://contoso.sharepoint.com/" + "a".repeat(PackagesUrl.MAX_LENGTH);
        assertThatThrownBy(() -> PackagesUrl.normalize(url)).isInstanceOf(InvalidPackagesUrlException.class);
    }
}
