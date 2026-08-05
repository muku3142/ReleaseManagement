package com.evision.releasemgmt.adapters.web.dto;

public record ReleaseAddResultResponse(ReleaseResponse release, int added, int alreadyPresent) {
}
