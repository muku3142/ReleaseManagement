package com.evision.releasemgmt.domain.dashboard;

// null means "no data", never zero.
public record TicketChurn(String ticketKey, Long commitsToDone, Long postDoneCommits, Integer reopens,
                          boolean keyTaggedOnly, boolean outlier) {
}
