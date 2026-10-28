package com.evision.releasemgmt.adapters.briefing;

import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.ports.BriefingReadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// The one reader of the Claude result files: file names come from the directory listing filtered by the
// enum-derived task prefix, never from a request.
@Component
public class FileBriefingReadAdapter implements BriefingReadPort {

    private static final Logger log = LoggerFactory.getLogger(FileBriefingReadAdapter.class);
    private static final int MAX_RUNS = 60;
    private static final int HEADLINE_MAX = 140;
    private static final int BULLET_MAX = 120;

    private final BriefingsProperties props;
    private final ObjectMapper mapper;
    private final BriefingFiles.Reader reader;

    @Autowired
    public FileBriefingReadAdapter(BriefingsProperties props, ObjectMapper mapper) {
        this(props, mapper, BriefingFiles::readBounded);
    }

    FileBriefingReadAdapter(BriefingsProperties props, ObjectMapper mapper, BriefingFiles.Reader reader) {
        this.props = props;
        this.mapper = mapper;
        this.reader = reader;
    }

    @Override
    public BriefingFeed feed(BriefingTask task) {
        Path dir = props.dir();
        if (Files.notExists(dir)) {
            return new BriefingFeed(task, List.of(), false);
        }
        List<String> names;
        String prefix = task.id() + "-";
        try (Stream<Path> listing = Files.list(dir)) {
            names = listing.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .limit(MAX_RUNS)
                    .toList();
        } catch (IOException | RuntimeException e) {
            return unreadable(task, e.getClass().getSimpleName());
        }
        List<BriefingRun> runs = new ArrayList<>();
        for (String name : names) {
            try {
                Optional<byte[]> bytes = reader.read(dir, name);
                if (bytes.isPresent()) {
                    parseRow(mapper.readTree(bytes.get())).ifPresent(runs::add);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("briefing run file skipped task={} exceptionType={}", task.id(), e.getClass().getSimpleName());
            }
        }
        runs.sort(Comparator.comparing(BriefingRun::runAt).reversed());
        return new BriefingFeed(task, List.copyOf(runs), false);
    }

    @Override
    public boolean directoryReadable() {
        Path dir = props.dir();
        return Files.isDirectory(dir) && Files.isReadable(dir);
    }

    private static BriefingFeed unreadable(BriefingTask task, String exceptionType) {
        log.warn("briefing feed unreadable task={} exceptionType={}", task.id(), exceptionType);
        return new BriefingFeed(task, List.of(), true);
    }

    private static Optional<BriefingRun> parseRow(JsonNode row) {
        if (!row.isObject()) {
            return Optional.empty();
        }
        Optional<Instant> runAt = parseInstant(text(row, "run_at"));
        Optional<BriefingStatus> status = BriefingStatus.fromClaudeRow(text(row, "status"));
        JsonNode attentionNode = row.get("attention");
        boolean attentionAbsent = attentionNode == null || attentionNode.isNull();
        if (runAt.isEmpty() || status.isEmpty()
                || (!attentionAbsent && !(attentionNode.isIntegralNumber() && attentionNode.canConvertToInt()
                && attentionNode.asInt() >= 0))) {
            return Optional.empty();
        }
        return Optional.of(new BriefingRun(runAt.get(), status.get(),
                BriefingFiles.cleanText(text(row, "headline"), HEADLINE_MAX),
                attentionAbsent ? null : attentionNode.asInt(),
                BriefingFiles.cleanText(text(row, "h1"), BULLET_MAX),
                BriefingFiles.cleanText(text(row, "h2"), BULLET_MAX),
                BriefingFiles.cleanText(text(row, "h3"), BULLET_MAX)));
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && node.isString() ? node.asString() : null;
    }

    private static Optional<Instant> parseInstant(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(OffsetDateTime.parse(value).toInstant());
            } catch (DateTimeParseException alsoIgnored) {
                return Optional.empty();
            }
        }
    }
}
