package com.evision.releasemgmt.domain.release;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class PackagesUrl {

    public static final int MAX_LENGTH = 2048;

    private static final String SHAREPOINT_SUFFIX = ".sharepoint.com";

    private PackagesUrl() {
    }

    /** Returns the trimmed link, or null for a blank value (which clears the link). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidPackagesUrlException();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidPackagesUrlException();
        }
        // The parsed URI host is checked, never substring matching on the raw text (userinfo and path tricks).
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean plainAuthority = uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        if (!https || !plainAuthority || uri.getHost() == null || !allowedHost(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new InvalidPackagesUrlException();
        }
        return trimmed;
    }

    private static boolean allowedHost(String host) {
        return host.equals("onedrive.live.com")
                || host.equals("1drv.ms")
                || (host.endsWith(SHAREPOINT_SUFFIX) && host.length() > SHAREPOINT_SUFFIX.length());
    }
}
