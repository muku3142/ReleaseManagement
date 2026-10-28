package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.roadmap.InputChange;
import java.util.List;
import java.util.UUID;

public interface RoadmapHistoryRepository {

    /** Newest first. Append-only ledger: writes happen only inside RoadmapRepository.updateInputsUnderLock. */
    List<InputChange> historyOf(UUID itemId);
}
