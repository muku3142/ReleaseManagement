package com.evision.releasemgmt.domain.release;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;

import java.time.Instant;

public record ReleaseItem(TicketDetail ticket, Instant addedAt, CodeLinks codeLinks, String bundle) {

    public ReleaseItem(TicketDetail ticket, Instant addedAt, CodeLinks codeLinks) {
        this(ticket, addedAt, codeLinks, null);
    }

    public String ticketKey() {
        return ticket.key();
    }
}
