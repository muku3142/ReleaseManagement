package com.evision.releasemgmt.adapters.persistence.repository;

import java.util.UUID;

public interface ProductClientCountRow {

    UUID getProductId();

    Long getClients();
}
