package com.evision.releasemgmt.domain.dashboard;

import java.math.BigDecimal;
import java.util.List;

// INSIGHT-04: per-release total only; unestimated and unread tickets are counted, never summed as zero.
public record ReleaseStoryPoints(BigDecimal total, int estimated, int unestimated, int unread) {

    public static ReleaseStoryPoints of(List<ChurnItem> items) {
        int unread = (int) items.stream().filter(i -> !i.storyPointsRead()).count();
        List<BigDecimal> estimates = items.stream()
                .filter(ChurnItem::storyPointsRead)
                .map(ChurnItem::storyPoints)
                .filter(points -> points != null)
                .toList();
        int unestimated = items.size() - unread - estimates.size();
        BigDecimal total = estimates.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReleaseStoryPoints(total, estimates.size(), unestimated, unread);
    }
}
