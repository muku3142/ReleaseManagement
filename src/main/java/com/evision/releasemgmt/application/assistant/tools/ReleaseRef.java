package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Resolves the model's release argument (UUID, name, version or "name version") to exactly one release. */
@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class ReleaseRef {

    static final int MAX_LENGTH = 100;
    private static final int MAX_CANDIDATES = 5;

    private final ReleaseService releaseService;

    public ReleaseRef(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    public UUID resolve(Object releaseArgument) {
        String text = releaseArgument == null ? "" : releaseArgument.toString().trim();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new ToolArgumentException("release must be 1 to " + MAX_LENGTH + " characters");
        }
        UUID id = asUuid(text);
        if (id != null) {
            try {
                return releaseService.get(id).id();
            } catch (ReleaseNotFoundException e) {
                throw new ToolArgumentException(noMatch(text));
            }
        }
        String wanted = text.toLowerCase(Locale.ROOT);
        List<ReleaseSummary> matches = releaseService.search(text).stream()
                .filter(r -> matchesExactly(r, wanted)).toList();
        if (matches.isEmpty()) {
            throw new ToolArgumentException(noMatch(text));
        }
        if (matches.size() > 1) {
            String candidates = matches.stream().limit(MAX_CANDIDATES)
                    .map(r -> r.name() + " " + r.version()).collect(Collectors.joining(", "));
            throw new ToolArgumentException("'" + text + "' is ambiguous, candidates: " + candidates);
        }
        return matches.getFirst().id();
    }

    private String noMatch(String text) {
        String message = "no release matches '" + text + "'";
        List<ReleaseSummary> known = releaseService.search("");
        if (known.isEmpty()) {
            return message;
        }
        return message + "; known releases: " + known.stream().limit(MAX_CANDIDATES)
                .map(r -> r.name() + " (" + r.version() + ")").collect(Collectors.joining(", "));
    }

    private static boolean matchesExactly(ReleaseSummary release, String wanted) {
        String name = release.name().toLowerCase(Locale.ROOT);
        String version = release.version().toLowerCase(Locale.ROOT);
        return name.equals(wanted) || version.equals(wanted) || (name + " " + version).equals(wanted);
    }

    private static UUID asUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
