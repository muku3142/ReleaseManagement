package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;

public record CommitLinkResponse(String sha, String shortSha, String message, String author, Instant authoredAt,
                                 String url) {
}
