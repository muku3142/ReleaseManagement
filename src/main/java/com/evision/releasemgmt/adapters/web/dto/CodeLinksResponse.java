package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.List;

public record CodeLinksResponse(String status, String reason, boolean incomplete, Instant attemptedAt,
                                String commitsSource, List<PullRequestLinkResponse> pullRequests,
                                List<CommitLinkResponse> commits, List<String> branches) {
}
