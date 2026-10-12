package com.evision.releasemgmt.adapters.web.dto;

import java.util.UUID;

public record StageResponse(UUID id, String name, int position, boolean production, boolean retired) {
}
