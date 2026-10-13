package com.evision.ragservice.adapters.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record PutDocumentRequest(@NotBlank String title, @NotNull String text,
                                 @Pattern(regexp = "https?://\\S{1,2040}") String url,
                                 Map<String, Object> metadata) {
}
