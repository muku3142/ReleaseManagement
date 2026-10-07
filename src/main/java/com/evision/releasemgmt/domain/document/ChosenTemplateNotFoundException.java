package com.evision.releasemgmt.domain.document;

// D-05.4-02: an unknown templateId at generation is a rejection, never a silent fallback to the default;
// distinct from TEMPLATE_NOT_FOUND, which is the template route's own 404.
public class ChosenTemplateNotFoundException extends RuntimeException {
}
