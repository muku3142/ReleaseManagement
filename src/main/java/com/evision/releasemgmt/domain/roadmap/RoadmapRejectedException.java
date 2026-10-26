package com.evision.releasemgmt.domain.roadmap;

public class RoadmapRejectedException extends RuntimeException {

    private final RoadmapRejection reason;

    public RoadmapRejectedException(RoadmapRejection reason) {
        super(reason.name());
        this.reason = reason;
    }

    public RoadmapRejection reason() {
        return reason;
    }
}
