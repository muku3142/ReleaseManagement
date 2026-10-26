package com.evision.releasemgmt.adapters.web.dto;

import jakarta.validation.constraints.Size;

public record PackagesUrlRequest(@Size(max = 2048) String url) {
}
