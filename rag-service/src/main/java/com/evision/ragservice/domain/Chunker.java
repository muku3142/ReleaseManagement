package com.evision.ragservice.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Chunker {

    private static final String SEPARATOR = "\n\n";
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s.*");
    private static final Pattern BLANK_LINE = Pattern.compile("\n[ \t]*\n");

    private record Unit(int section, String text) {
    }

    private final int maxChars;
    private final int overlapChars;

    public Chunker(int maxChars, int overlapChars) {
        if (overlapChars * 2 >= maxChars) {
            throw new IllegalArgumentException("overlapChars must be less than half of maxChars");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public static String withHeader(String title, String passage) {
        return title + SEPARATOR + passage;
    }

    public List<String> chunk(String title, String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isBlank()) {
            return List.of();
        }
        int budget = maxChars - withHeader(title, "").length();
        if (budget <= overlapChars * 2) {
            throw new InvalidContentException("title is too long to leave room for content");
        }

        List<String> passages = new ArrayList<>();
        String current = "";
        boolean carriesOnlyOverlap = true;
        int currentSection = -1;
        for (Unit unit : units(normalized, budget)) {
            if (carriesOnlyOverlap && unit.section() != currentSection) {
                current = "";
            }
            String rest = unit.text();
            while (!rest.isEmpty()) {
                if (fits(current, rest, budget)) {
                    current = join(current, rest);
                    carriesOnlyOverlap = false;
                    currentSection = unit.section();
                    rest = "";
                } else if (!carriesOnlyOverlap) {
                    passages.add(current);
                    current = unit.section() == currentSection ? tail(current) : "";
                    carriesOnlyOverlap = true;
                } else {
                    int cut = cutPoint(rest, budget - (current.isEmpty() ? 0 : current.length() + SEPARATOR.length()));
                    String passage = join(current, rest.substring(0, cut).strip());
                    passages.add(passage);
                    current = tail(passage);
                    currentSection = unit.section();
                    rest = rest.substring(cut).strip();
                }
            }
        }
        if (!carriesOnlyOverlap) {
            passages.add(current);
        }
        return passages.stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    private static boolean fits(String current, String addition, int budget) {
        return (current.isEmpty() ? 0 : current.length() + SEPARATOR.length()) + addition.length() <= budget;
    }

    private static String join(String current, String addition) {
        return current.isEmpty() ? addition : current + SEPARATOR + addition;
    }

    private static int cutPoint(String text, int room) {
        int lastSpace = -1;
        for (int i = Math.min(room, text.length() - 1); i > 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                lastSpace = i;
                break;
            }
        }
        return lastSpace > 0 ? lastSpace : room;
    }

    private String tail(String passage) {
        int start = Math.max(0, passage.length() - overlapChars);
        String tail = passage.substring(start);
        if (start > 0 && !Character.isWhitespace(passage.charAt(start - 1))) {
            int firstSpace = indexOfWhitespace(tail);
            tail = firstSpace < 0 ? "" : tail.substring(firstSpace);
        }
        return tail.strip();
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<Unit> units(String text, int budget) {
        List<Unit> units = new ArrayList<>();
        int section = 0;
        for (String body : sections(text)) {
            if (body.length() <= budget) {
                units.add(new Unit(section, body));
            } else {
                for (String paragraph : BLANK_LINE.split(body)) {
                    if (!paragraph.isBlank()) {
                        units.add(new Unit(section, paragraph.strip()));
                    }
                }
            }
            section++;
        }
        return units;
    }

    private static List<String> sections(String text) {
        List<String> sections = new ArrayList<>();
        StringBuilder section = new StringBuilder();
        boolean inFence = false;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("```")) {
                inFence = !inFence;
            }
            if (!inFence && HEADING.matcher(line).matches() && !section.isEmpty()) {
                addIfNotBlank(sections, section);
                section.setLength(0);
            }
            section.append(line).append('\n');
        }
        addIfNotBlank(sections, section);
        return sections;
    }

    private static void addIfNotBlank(List<String> sections, StringBuilder section) {
        String body = section.toString().strip();
        if (!body.isEmpty()) {
            sections.add(body);
        }
    }
}
