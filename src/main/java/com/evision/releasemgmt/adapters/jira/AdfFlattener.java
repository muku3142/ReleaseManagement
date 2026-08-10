package com.evision.releasemgmt.adapters.jira;

import tools.jackson.databind.JsonNode;

public final class AdfFlattener {

    private static final int MAX_NODES = 5000;
    private static final int MAX_DEPTH = 50;

    public String flatten(JsonNode adfDoc) {
        if (adfDoc == null || !adfDoc.isObject()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        walk(adfDoc, out, 0, 0, new int[]{0, 0});
        return out.toString();
    }

    private void walk(JsonNode node, StringBuilder out, int listDepth, int depth, int[] state) {
        if (bounded(out, depth, state)) {
            return;
        }
        state[0]++;

        String type = node.path("type").asText("");
        switch (type) {
            case "doc" -> walkChildren(node, out, listDepth, depth, state);
            case "paragraph" -> {
                walkChildren(node, out, listDepth, depth, state);
                out.append('\n');
            }
            case "heading" -> {
                int level = Math.clamp(node.path("attrs").path("level").asInt(1), 1, 6);
                out.append("#".repeat(level)).append(' ');
                walkChildren(node, out, listDepth, depth + 1, state);
                out.append('\n');
            }
            case "text" -> out.append(applyMarks(node));
            case "hardBreak" -> out.append('\n');
            case "bulletList", "orderedList" -> walkChildren(node, out, listDepth + 1, depth + 1, state);
            case "listItem" -> {
                out.append("  ".repeat(Math.max(0, listDepth - 1))).append("- ");
                walkChildren(node, out, listDepth, depth + 1, state);
            }
            case "codeBlock" -> {
                out.append("```\n");
                walkChildren(node, out, listDepth, depth + 1, state);
                if (out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                out.append("```\n");
            }
            default -> {
                // Unknown node type (table, panel, media, mention, emoji, status - all explicitly
                // out of scope per D-02-04): degrade to its own text content, never throw, never drop.
                if (node.has("text")) {
                    out.append(node.path("text").asText(""));
                }
                walkChildren(node, out, listDepth, depth + 1, state);
            }
        }
    }

    private void walkChildren(JsonNode node, StringBuilder out, int listDepth, int depth, int[] state) {
        for (JsonNode child : node.path("content")) {
            if (state[1] == 1) {
                return;
            }
            walk(child, out, listDepth, depth, state);
        }
    }

    private boolean bounded(StringBuilder out, int depth, int[] state) {
        if (state[1] == 1) {
            return true;
        }
        if (state[0] >= MAX_NODES || depth >= MAX_DEPTH) {
            state[1] = 1;
            out.append("\n[content truncated]");
            return true;
        }
        return false;
    }

    private String applyMarks(JsonNode textNode) {
        String text = textNode.path("text").asText("");
        for (JsonNode mark : textNode.path("marks")) {
            text = switch (mark.path("type").asText("")) {
                case "strong" -> "**" + text + "**";
                case "em" -> "*" + text + "*";
                case "code" -> "`" + text + "`";
                case "link" -> text + " (" + mark.path("attrs").path("href").asText("") + ")";
                default -> text;
            };
        }
        return text;
    }
}
