package com.evision.releasemgmt.application;

import java.util.List;

// Two producers, two content types: RenderService.render() puts flexmark-rendered HTML here,
// RenderService.resolveText() puts Mustache-resolved source text (Markdown, or a one-line chrome
// string) here. The component is deliberately NOT named after either one - a name like html() read
// as a guarantee at the call site and is how document generation came to persist HTML into a field
// documented as Markdown. Read the method you called, not this accessor.
public record RenderResult(String output, List<String> unknownTokens) {
}
