package com.evision.releasemgmt.domain.template;

// D-04-11: a wall-clock timeout or collection-cap breach in RenderService. Its copy is fixed by the
// UI spec ("validation took too long or produced too much content"), so it carries no payload.
public class RenderBoundExceededException extends RuntimeException {
}
