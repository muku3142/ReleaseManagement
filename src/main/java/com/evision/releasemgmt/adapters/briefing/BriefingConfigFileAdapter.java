package com.evision.releasemgmt.adapters.briefing;

import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.briefing.BriefingClaudeConfig;
import com.evision.releasemgmt.domain.briefing.BriefingQueries;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.ports.BriefingConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Reads only coverage and default thresholds; the Jira URL never comes from this host-edited file (D-17.1-16).
@Component
public class BriefingConfigFileAdapter implements BriefingConfigSource {

    private static final Logger log = LoggerFactory.getLogger(BriefingConfigFileAdapter.class);
    private static final Set<BriefingTask> JIRA_TASKS =
            Set.of(BriefingTask.JIRA_DIGEST, BriefingTask.SPRINT_CHECK, BriefingTask.WEEKLY_STATUS);

    private final BriefingsProperties props;
    private final ObjectMapper mapper;

    public BriefingConfigFileAdapter(BriefingsProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public BriefingClaudeConfig read() {
        Path config = props.config();
        if (config == null || config.getFileName() == null) {
            return BriefingClaudeConfig.UNREADABLE;
        }
        try {
            Path parent = config.toAbsolutePath().getParent();
            Optional<byte[]> bytes = BriefingFiles.readBounded(parent, config.getFileName().toString());
            if (bytes.isEmpty()) {
                return BriefingClaudeConfig.UNREADABLE;
            }
            JsonNode root = mapper.readTree(bytes.get());
            if (root == null || !root.isObject()) {
                return unreadable("NotAnObject");
            }
            return parse(root);
        } catch (IOException | RuntimeException e) {
            return unreadable(e.getClass().getSimpleName());
        }
    }

    private static BriefingClaudeConfig parse(JsonNode root) {
        JsonNode tasks = root.path("tasks");
        Set<String> globalProjects = projectKeys(root.path("jira").path("projects"));
        Map<BriefingTask, Set<String>> covered = new EnumMap<>(BriefingTask.class);
        for (BriefingTask task : JIRA_TASKS) {
            JsonNode own = tasks.path(task.id()).path("projects");
            covered.put(task, own.isArray() ? projectKeys(own) : globalProjects);
        }
        JsonNode digest = tasks.path(BriefingTask.JIRA_DIGEST.id());
        return new BriefingClaudeConfig(true, Map.copyOf(covered),
                bounded(digest.path("staleAfterWorkingDays"), 1, 30),
                bounded(digest.path("dueSoonDays"), 1, 60),
                bounded(tasks.path(BriefingTask.SPRINT_CHECK.id()).path("backlogItemsWhenNoFutureSprint"), 1, 100),
                bounded(root.path("output").path("keepRuns"), 10, 500));
    }

    private static Set<String> projectKeys(JsonNode array) {
        Set<String> keys = new LinkedHashSet<>();
        if (array.isArray()) {
            for (JsonNode element : array) {
                if (element.isString()) {
                    String key = element.asString().strip().toUpperCase(Locale.ROOT);
                    if (BriefingQueries.PROJECT_KEY.matcher(key).matches()) {
                        keys.add(key);
                    }
                }
            }
        }
        return Set.copyOf(keys);
    }

    private static Integer bounded(JsonNode node, int min, int max) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        int value = node.asInt();
        return value >= min && value <= max ? value : null;
    }

    private static BriefingClaudeConfig unreadable(String exceptionType) {
        log.warn("briefing config unreadable exceptionType={}", exceptionType);
        return BriefingClaudeConfig.UNREADABLE;
    }
}
