package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.ports.RagChatPort;
import com.evision.releasemgmt.ports.RagTransformPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lets the self-hosted model rewrite only the headline and summary; any failure keeps the rule text (D-17.1-08). */
@Service
public class BriefingHeadlineService {

    private static final Logger log = LoggerFactory.getLogger(BriefingHeadlineService.class);

    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");
    private static final int MAX_FACTS = 1500;
    private static final int MAX_FACT_ITEMS = 3;
    private static final int MAX_HEADLINE = 140;
    private static final int MAX_SUMMARY_LINES = 2;
    private static final int MAX_SUMMARY = 400;

    private final ObjectProvider<RagTransformPort> transform;
    private final BriefingsProperties props;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public BriefingHeadlineService(ObjectProvider<RagTransformPort> transform, BriefingsProperties props) {
        this.transform = transform;
        this.props = props;
    }

    public BriefingContent enrich(BriefingContent content, String productName) {
        // No bean when the assistant is switched off; a direct dependency would break startup.
        RagTransformPort port = transform.getIfAvailable();
        if (port == null) {
            return content;
        }
        long started = System.nanoTime();
        String facts = facts(content, productName);
        BriefingContent result = ask(port, facts)
                .map(answer -> validated(content, answer, keysIn(facts)))
                .orElse(content);
        log.info("briefing headline outcome={} durationMs={}", result == content ? "kept" : "model",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return result;
    }

    private Optional<String> ask(RagTransformPort port, String facts) {
        StringBuilder answer = new StringBuilder();
        RagChatPort.ChatListener listener = new RagChatPort.ChatListener() {
            @Override
            public void token(String text) {
                answer.append(text);
            }

            @Override
            public void roundDiscarded() {
                answer.setLength(0);
            }
        };
        Future<TransformOutcome> future = executor.submit(
                () -> port.transform(facts, props.headline().instruction(), listener));
        try {
            TransformOutcome outcome = future.get(props.headline().timeout().toMillis(), TimeUnit.MILLISECONDS);
            return outcome instanceof TransformOutcome.Done ? Optional.of(answer.toString())
                    : Optional.empty();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // Timeout, or the port threw: the run must not wait or fail because of the model.
            future.cancel(true);
            return Optional.empty();
        }
    }

    private static String facts(BriefingContent content, String productName) {
        StringBuilder facts = new StringBuilder()
                .append(productName).append(": status ").append(content.status().wire())
                .append(", attention ").append(content.attention()).append('.');
        content.sections().stream()
                .flatMap(section -> section.items().stream())
                .filter(item -> item.issueKey() != null)
                .limit(MAX_FACT_ITEMS)
                .forEach(item -> facts.append('\n').append(item.issueKey()).append(' ').append(item.text()));
        return facts.length() > MAX_FACTS ? facts.substring(0, MAX_FACTS) : facts.toString();
    }

    private static BriefingContent validated(BriefingContent content, String answer, Set<String> factKeys) {
        List<String> lines = answer.strip().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty() || lines.getFirst().length() > MAX_HEADLINE) {
            return content;
        }
        String headline = lines.getFirst();
        List<String> summaryLines = lines.subList(1, Math.min(lines.size(), 1 + MAX_SUMMARY_LINES));
        String summary = summaryLines.isEmpty() ? content.summary() : String.join("\n", summaryLines);
        if (summary != null && summary.length() > MAX_SUMMARY) {
            return content;
        }
        // Jira text is untrusted; a key the facts never mentioned is the signature of an injected or invented line.
        if (!factKeys.containsAll(keysIn(headline + "\n" + String.join("\n", summaryLines)))) {
            return content;
        }
        return new BriefingContent(content.status(), content.attention(), headline, summary, content.sections());
    }

    private static Set<String> keysIn(String text) {
        Set<String> keys = new HashSet<>();
        Matcher matcher = ISSUE_KEY.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group());
        }
        return keys;
    }
}
