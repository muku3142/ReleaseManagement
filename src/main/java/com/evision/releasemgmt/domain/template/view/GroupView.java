package com.evision.releasemgmt.domain.template.view;

import java.util.List;

// D-04-02: grouping is pre-computed here because Mustache is logic-less and cannot group. The
// dimension is hard-wired to issue type (tickets) / category (manual items) for this phase;
// DOC-02's component/epic/label dimensions are Phase 5 and slot into this same shape.
public record GroupView(String groupName, List<ItemView> items) {
}
