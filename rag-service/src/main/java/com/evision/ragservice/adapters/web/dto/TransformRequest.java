package com.evision.ragservice.adapters.web.dto;

import com.evision.ragservice.application.TransformService;
import jakarta.validation.constraints.NotBlank;

public record TransformRequest(@NotBlank String text, @NotBlank String instruction) {

    @Override
    public String toString() {
        return "TransformRequest[textChars=" + length(text) + ", instructionChars=" + length(instruction) + "]";
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    public TransformService.Command toCommand() {
        return new TransformService.Command(text, instruction);
    }
}
