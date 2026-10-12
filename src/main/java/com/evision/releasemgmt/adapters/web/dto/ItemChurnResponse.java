package com.evision.releasemgmt.adapters.web.dto;

public record ItemChurnResponse(Long commitsToDone, Long postDoneCommits, Integer reopens,
                                boolean keyTaggedOnly, boolean outlier) {
}
