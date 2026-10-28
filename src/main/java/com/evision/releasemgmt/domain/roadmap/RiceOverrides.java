package com.evision.releasemgmt.domain.roadmap;

// The reason behind each overridden calculated value; null means the field shows its calculated value.
public record RiceOverrides(String reach, String impact, String confidence) {

    public static final RiceOverrides NONE = new RiceOverrides(null, null, null);

    public String reasonFor(RiceField field) {
        return switch (field) {
            case REACH -> reach;
            case IMPACT -> impact;
            case CONFIDENCE -> confidence;
            case EFFORT -> null;
        };
    }

    public RiceOverrides with(RiceField field, String reason) {
        return switch (field) {
            case REACH -> new RiceOverrides(reason, impact, confidence);
            case IMPACT -> new RiceOverrides(reach, reason, confidence);
            case CONFIDENCE -> new RiceOverrides(reach, impact, reason);
            case EFFORT -> throw new IllegalArgumentException("Effort is never calculated");
        };
    }
}
