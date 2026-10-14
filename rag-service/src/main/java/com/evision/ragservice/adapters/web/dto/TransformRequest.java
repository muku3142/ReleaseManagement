package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.application.TransformService;
import jakarta.validation.constraints.NotBlank;

public record TransformRequest(@NotBlank String text, @NotBlank String instruction) {

    public TransformService.Command toCommand() {
        return new TransformService.Command(text, instruction);
    }
}
