package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingTask;

public interface BriefingReadPort {

    // Errors come back as readError=true with an empty list, never thrown.
    BriefingFeed feed(BriefingTask task);

    boolean directoryReadable();
}
