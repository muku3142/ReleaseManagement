package com.evision.releasemgmt.domain.release;

public record AddTicketsResult(Release release, int added, int alreadyPresent) {
}
