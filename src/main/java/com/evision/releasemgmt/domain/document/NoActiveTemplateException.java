package com.evision.releasemgmt.domain.document;

// D-05-10: a document type having zero active templates is a supported state, not a broken one -
// this is the defensive backstop for a generate call the UI should already have prevented.
public class NoActiveTemplateException extends RuntimeException {
}
