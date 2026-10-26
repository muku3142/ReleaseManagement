package com.evision.releasemgmt.domain.portfolio;

import java.util.UUID;

/** name is null when the id is unknown. */
public record RejectedClient(UUID clientId, String name, Reason reason) {

    public enum Reason { NOT_LINKED, ARCHIVED }
}
