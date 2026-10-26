package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.briefing.BriefingClaudeConfig;

public interface BriefingConfigSource {

    /** Never throws; a missing or unusable file is BriefingClaudeConfig.UNREADABLE. */
    BriefingClaudeConfig read();
}
