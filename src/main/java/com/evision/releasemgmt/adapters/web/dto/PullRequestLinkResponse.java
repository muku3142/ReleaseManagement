package com.evision.releasemgmt.adapters.web.dto;

public record PullRequestLinkResponse(String id, String title, String status, String url, String sourceBranch,
                                      String targetBranch, boolean countsTowardCommits) {
}
