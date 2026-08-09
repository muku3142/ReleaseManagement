package com.evision.releasemgmt.domain.template.view;

// A record rather than a bare String so a template writes {{#comments}}{{text}}{{/comments}} and
// never needs Mustache's implicit-this {{.}} - which would be a construct outside D-04-06's subset.
public record CommentView(String text) {
}
