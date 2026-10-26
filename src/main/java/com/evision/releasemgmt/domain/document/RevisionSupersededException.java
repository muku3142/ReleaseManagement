package com.evision.releasemgmt.domain.document;

public class RevisionSupersededException extends RuntimeException {

    public RevisionSupersededException() {
        super("The document has a newer revision");
    }
}
