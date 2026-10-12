package com.evision.releasemgmt.domain.enrichment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

// Every ADO URL is built from these validated parts and nothing from the payload is fetched (SSRF guard).
public record AdoRepoRef(String organization, String project, String repositoryWebUrl) {

    public static final String ORGANIZATION_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$";
    private static final Pattern ORGANIZATION = Pattern.compile(ORGANIZATION_REGEX);
    private static final String HOST = "dev.azure.com";
    private static final int MIN_SEGMENTS = 4;

    public static boolean isTrustedUrl(String url) {
        return parse(url).isPresent();
    }

    public static Optional<AdoRepoRef> fromPullRequestUrl(String url) {
        Optional<URI> parsed = parse(url);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        URI uri = parsed.get();
        String[] raw = segments(uri.getRawPath());
        String[] decoded = segments(uri.getPath());
        if (raw.length < MIN_SEGMENTS || decoded.length != raw.length || !"_git".equals(raw[2])
                || !ORGANIZATION.matcher(raw[0]).matches()) {
            return Optional.empty();
        }
        return Optional.of(new AdoRepoRef(raw[0], decoded[1],
                "https://" + HOST + "/" + raw[0] + "/" + raw[1] + "/_git/" + raw[3]));
    }

    private static Optional<URI> parse(String url) {
        if (url == null) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            boolean trusted = "https".equals(uri.getScheme()) && HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null && uri.getPort() == -1;
            return trusted ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static String[] segments(String path) {
        return Arrays.stream(path == null ? new String[0] : path.split("/"))
                .filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
