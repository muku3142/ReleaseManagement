package com.evision.ragservice.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateClientRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,100}") String name) {
}
