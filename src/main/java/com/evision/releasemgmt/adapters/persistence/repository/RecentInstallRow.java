package com.evision.releasemgmt.adapters.persistence.repository;

import java.util.UUID;

public interface RecentInstallRow {

    UUID getClientId();

    UUID getProductId();
}
