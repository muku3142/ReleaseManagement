package com.evision.releasemgmt.domain.template;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

// D-04-06's enforcement point: a hand-written character scan (not a single regex - a regex cannot
// reliably tell {{{x}}} from {{ + {x} + }}) that rejects every Mustache construct outside the
// allowed variable/section/inverted-section/comment subset, and collects any placeholder token that
// is not in PlaceholderCatalog for its scope as an unknown, non-blocking warning (D-04-07/D-04-08).
public final class TemplateScanner {

    // Mirrors AdfFlattener's MAX_NODES bounding idiom (adapters/jira/AdfFlattener.java:7) - count
    // as the walk proceeds and stop rather than let an adversarial template stall the scan.
    private static final int MAX_TAGS = 5000;

    public ScanResult scan(String source, String field) {
        if (source == null || source.isEmpty()) {
            return new ScanResult(List.of());
        }

        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        Deque<Frame> stack = new ArrayDeque<>();
        int tagCount = 0;
        int length = source.length();
        int i = 0;

        while (i < length) {
            int tagStart = source.indexOf("{{", i);
            if (tagStart == -1) {
                break;
            }

            tagCount++;
            if (tagCount > MAX_TAGS) {
                throw new TemplateMalformedException(field, "Template has too many placeholders.");
            }

            int contentStart = tagStart + 2;
            if (contentStart < length && source.charAt(contentStart) == '{') {
                throw new TemplateMalformedException(field,
                        "Unescaped variables ({{{ }}} and {{& }}) are not supported: "
                                + rawUnescapedTag(source, tagStart, contentStart) + ".");
            }

            int closeIdx = source.indexOf("}}", contentStart);
            if (closeIdx == -1) {
                String snippet = source.substring(tagStart, Math.min(length, tagStart + 40));
                throw new TemplateMalformedException(field, "Unterminated placeholder: " + snippet);
            }

            String inner = source.substring(contentStart, closeIdx).stripLeading();
            char sigil = inner.isEmpty() ? ' ' : inner.charAt(0);
            PlaceholderScope currentScope = stack.isEmpty() ? PlaceholderScope.ROOT : stack.peek().scope();

            switch (sigil) {
                case '!' -> {
                    // comment: ignore
                }
                case '>' -> {
                    String name = inner.substring(1).trim();
                    throw new TemplateMalformedException(field, "Partials are not supported: {{>" + name + "}}.");
                }
                case '&' -> {
                    String rawTag = source.substring(tagStart, closeIdx + 2);
                    throw new TemplateMalformedException(field,
                            "Unescaped variables ({{{ }}} and {{& }}) are not supported: " + rawTag + ".");
                }
                case '=' -> throw new TemplateMalformedException(field, "Custom delimiters are not supported.");
                case '#', '^' -> openSection(inner, currentScope, stack, unknown);
                case '/' -> closeSection(inner, field, stack);
                default -> {
                    String name = inner.trim();
                    if (PlaceholderCatalog.find(currentScope, name).isEmpty()) {
                        unknown.add(name);
                    }
                }
            }

            i = closeIdx + 2;
        }

        if (!stack.isEmpty()) {
            Frame top = stack.peek();
            throw new TemplateMalformedException(field,
                    "Unclosed section: {{#" + top.name() + "}} has no matching {{/" + top.name() + "}}.");
        }

        return new ScanResult(List.copyOf(unknown));
    }

    private static void openSection(String inner, PlaceholderScope currentScope, Deque<Frame> stack,
                                     LinkedHashSet<String> unknown) {
        String name = inner.substring(1).trim();
        Optional<PlaceholderEntry> entry = PlaceholderCatalog.find(currentScope, name);
        if (entry.isEmpty()) {
            unknown.add(name);
            stack.push(new Frame(name, currentScope));
        } else if (entry.get().kind() == PlaceholderKind.SECTION && entry.get().pushes() != null) {
            stack.push(new Frame(name, entry.get().pushes()));
        } else {
            stack.push(new Frame(name, currentScope));
        }
    }

    private static void closeSection(String inner, String field, Deque<Frame> stack) {
        String name = inner.substring(1).trim();
        if (stack.isEmpty()) {
            throw new TemplateMalformedException(field, "Unexpected closing tag: {{/" + name + "}}.");
        }
        Frame top = stack.peek();
        if (!top.name().equals(name)) {
            throw new TemplateMalformedException(field,
                    "Mismatched closing tag: {{#" + top.name() + "}} is closed by {{/" + name + "}}.");
        }
        stack.pop();
    }

    private static String rawUnescapedTag(String source, int tagStart, int contentStart) {
        int length = source.length();
        int closeIdx = source.indexOf("}}", contentStart + 1);
        int end;
        if (closeIdx == -1) {
            end = length;
        } else if (closeIdx + 2 < length && source.charAt(closeIdx + 2) == '}') {
            end = closeIdx + 3;
        } else {
            end = closeIdx + 2;
        }
        return source.substring(tagStart, end);
    }

    private record Frame(String name, PlaceholderScope scope) {
    }
}
