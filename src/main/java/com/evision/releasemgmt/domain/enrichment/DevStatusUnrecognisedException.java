package com.evision.releasemgmt.domain.enrichment;

// Summary and detail disagree: schema drift or a wrong applicationType, surfaced as FAILED/DEV_STATUS_UNRECOGNISED
// rather than a silent NO_LINK.
public class DevStatusUnrecognisedException extends RuntimeException {
}
