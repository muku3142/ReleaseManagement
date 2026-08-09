package com.evision.releasemgmt.domain.template;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// D-04-13: freely editable and freely deletable, so unlike Release there is no requireOpen()-style
// lifecycle guard here. warnings holds the unknown placeholders the last save's validation render
// found (D-04-07) - a save succeeds with warnings, it is not blocked by them.
public record Template(UUID id, String name, TemplateDocumentType documentType, String body,
                       boolean active, ChromeFields chrome, LogoMetadata logo,
                       List<String> warnings, Instant createdAt, String createdBy,
                       Instant updatedAt, String updatedBy) {

    public static Template create(String name, TemplateDocumentType documentType, String actor,
                                  Instant now) {
        return new Template(UUID.randomUUID(), name, documentType, "", false, ChromeFields.empty(),
                null, List.of(), now, actor, now, actor);
    }
}
