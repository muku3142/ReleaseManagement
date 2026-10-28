package com.evision.releasemgmt.domain.portfolio;

import java.util.UUID;

public record ReleaseProduct(UUID id, String name, boolean archived, boolean hasInstalls) {}
