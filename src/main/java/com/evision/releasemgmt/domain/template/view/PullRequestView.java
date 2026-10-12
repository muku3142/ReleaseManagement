package com.evision.releasemgmt.domain.template.view;

// D-04-18 defined this; Phase 6 fills it from stored code links, LINKED items only (D-06-07).
public record PullRequestView(String id, String title, String status, String url) {
}
