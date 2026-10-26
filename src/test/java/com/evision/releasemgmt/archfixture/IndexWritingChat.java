package com.evision.releasemgmt.archfixture;

import com.evision.releasemgmt.ports.RagIndexPort;

/** ArchUnit non-vacuity fixture: a chat path that depends on the index writer. Never wired into the application. */
public class IndexWritingChat {

    private RagIndexPort index;

    public RagIndexPort index() {
        return index;
    }
}
