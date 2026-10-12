package com.evision.releasemgmt.domain.release;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;

import java.time.Instant;

public record ReleaseItem(TicketDetail ticket, Instant addedAt, CodeLinks codeLinks) {

    public String ticketKey() {
        return ticket.key();
    }
}
