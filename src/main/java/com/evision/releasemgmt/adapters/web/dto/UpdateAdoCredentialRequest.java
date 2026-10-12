package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.domain.enrichment.AdoRepoRef;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** A blank token means "change only the expiry date of the stored organization". */
public record UpdateAdoCredentialRequest(
        @NotBlank @Pattern(regexp = AdoRepoRef.ORGANIZATION_REGEX) String organization,
        @Size(max = 512) String token,
        @NotNull @FutureOrPresent LocalDate expiresOn) {
}
