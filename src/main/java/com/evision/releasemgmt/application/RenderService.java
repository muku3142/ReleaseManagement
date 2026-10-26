package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.template.RenderBoundExceededException;
import com.evision.releasemgmt.domain.template.ScanResult;
import com.evision.releasemgmt.domain.template.TemplateScanner;
import com.evision.releasemgmt.domain.template.view.GroupView;
import com.evision.releasemgmt.domain.template.view.ItemView;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import com.evision.releasemgmt.ports.RenderEnginePort;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// D-04-11: the single bounded render entry point. Save-time validation and Phase 5's document
// generation both go through this one class, so TMPL-05's wall-clock timeout and collection-size
// cap are enforced exactly once, not re-implemented per caller.
@Service
public class RenderService {

    // TemplateScanner is a plain domain class (no dependencies to inject) - see plan 04-01.
    private static final TemplateScanner SCANNER = new TemplateScanner();

    private final RenderEnginePort engine;
    private final Duration timeout;
    private final int maxCollectionSize;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // WR-05: jmustache does not poll for interruption, so cancel(true) on timeout cannot stop a
    // pathological render - it can only stop the caller waiting for it. Without this cap, a burst
    // of requests that each trip the timeout would accumulate an unbounded number of orphaned
    // virtual threads still burning CPU in the background. This bounds that accumulation: the
    // permit is released only when the underlying work genuinely finishes (see bounded() below),
    // not when a caller stops waiting, so at most maxConcurrentRenders renders - orphaned or not -
    // can ever be running at once.
    private final Semaphore concurrentRenders;

    public RenderService(RenderEnginePort engine,
                          @Value("${app.template.render.timeout}") Duration timeout,
                          @Value("${app.template.render.max-collection-size}") int maxCollectionSize,
                          @Value("${app.template.render.max-concurrent}") int maxConcurrentRenders) {
        this.engine = engine;
        this.timeout = timeout;
        this.maxCollectionSize = maxCollectionSize;
        this.concurrentRenders = new Semaphore(maxConcurrentRenders);
    }

    // Resolves Mustache placeholders, then parses the result as Markdown and renders it to HTML.
    // output() is HTML. Save-time validation is the only consumer - document generation must NOT
    // use this method, see resolveText.
    public RenderResult render(String source, ReleaseView view, String field) {
        ScanResult scan = SCANNER.scan(source, field);
        enforceCollectionCap(view);
        String safeSource = markUnknownTokens(source, scan.unknownTokens());
        Map<String, Object> context = RenderContextMapper.toContext(view);
        String html = bounded(() -> engine.render(safeSource, context));
        return new RenderResult(html, scan.unknownTokens());
    }

    // D-04-11's bounds are not optional for chrome either: a cover subtitle is resolved against the
    // same view and can breach the same collection cap. This is render() with the flexmark step
    // removed, not a second, looser path.
    //
    // Resolves Mustache placeholders and stops there. output() is the resolved source text,
    // unchanged in kind: Markdown in, Markdown out; a one-line chrome string in, a one-line chrome
    // string out. This is the bounded path that produces a DocumentSnapshot body.
    public RenderResult resolveText(String source, ReleaseView view, String field) {
        ScanResult scan = SCANNER.scan(source, field);
        enforceCollectionCap(view);
        String safeSource = markUnknownTokens(source, scan.unknownTokens());
        Map<String, Object> context = RenderContextMapper.toContext(view);
        String text = bounded(() -> engine.resolve(safeSource, context));
        return new RenderResult(text, scan.unknownTokens());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private String bounded(Callable<String> work) {
        if (!tryAcquireRenderPermit()) {
            // Every permit is held by a render that is still actually running - including ones a
            // past caller already gave up waiting on (see the release in the finally below). Refuse
            // rather than add yet another accumulating background task.
            throw new RenderBoundExceededException();
        }
        Callable<String> permitReleasingWork = () -> {
            try {
                return work.call();
            } finally {
                concurrentRenders.release();
            }
        };
        Future<String> future = executor.submit(permitReleasingWork);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            // cancel(true) interrupts the carrier thread but jmustache does not poll for
            // interruption, so a pathological render may keep running until it finishes. The
            // collection cap above is what actually bounds the work; this timeout only bounds how
            // long THIS caller waits for it - the permit above is what stops such orphaned renders
            // from accumulating without limit.
            throw new RenderBoundExceededException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private boolean tryAcquireRenderPermit() {
        try {
            return concurrentRenders.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Unlike AdfFlattener, which truncates and appends "[content truncated]", this throws - D-04-11
    // states that breaching either bound "surfaces as a ProblemDetail with a machine-readable
    // reason ... not a generic 500", and a silently truncated release document is worse than a
    // refused one.
    private void enforceCollectionCap(ReleaseView view) {
        checkBound(view.items().size());
        checkBound(view.groups().size());
        checkBound(view.products().size());
        for (GroupView group : view.groups()) {
            checkBound(group.items().size());
        }
        for (ItemView item : view.items()) {
            checkBound(item.comments().size());
            checkBound(item.commits().size());
            checkBound(item.pullRequests().size());
        }
    }

    private void checkBound(int size) {
        if (size > maxCollectionSize) {
            throw new RenderBoundExceededException();
        }
    }

    // D-04-08 wants a conspicuous marker naming the token, not a blank. Producing it here - from the
    // scanner's own list, before the source ever reaches the engine - means the guarantee is asserted
    // by this codebase rather than resting on a library's missing-key behaviour, which is precisely
    // the distinction D-04-06 draws for the rest of the subset.
    private static String markUnknownTokens(String source, List<String> unknownTokens) {
        if (unknownTokens.isEmpty()) {
            return source;
        }
        Set<String> unknown = new HashSet<>(unknownTokens);
        StringBuilder out = new StringBuilder();
        // Tracks, for each currently-open section, whether ITS opening tag was unknown - so the
        // matching {{/name}} is blanked only when it closes an unknown section, not whenever its
        // name happens to collide with an unrelated unknown token elsewhere in the template.
        Deque<Boolean> openSectionIsUnknown = new ArrayDeque<>();
        int length = source.length();
        int i = 0;
        while (i < length) {
            int tagStart = source.indexOf("{{", i);
            if (tagStart == -1) {
                out.append(source, i, length);
                break;
            }
            out.append(source, i, tagStart);

            int contentStart = tagStart + 2;
            int closeIdx = source.indexOf("}}", contentStart);
            String inner = source.substring(contentStart, closeIdx).stripLeading();
            char sigil = inner.isEmpty() ? ' ' : inner.charAt(0);

            switch (sigil) {
                case '#', '^' -> {
                    String name = inner.substring(1).trim();
                    boolean isUnknown = unknown.contains(name);
                    openSectionIsUnknown.push(isUnknown);
                    out.append(isUnknown ? marker(name) : source.substring(tagStart, closeIdx + 2));
                }
                case '/' -> {
                    boolean closesUnknown = !openSectionIsUnknown.isEmpty() && openSectionIsUnknown.pop();
                    if (!closesUnknown) {
                        out.append(source, tagStart, closeIdx + 2);
                    }
                }
                case '!' -> out.append(source, tagStart, closeIdx + 2);
                default -> {
                    String name = inner.trim();
                    out.append(unknown.contains(name) ? marker(name) : source.substring(tagStart, closeIdx + 2));
                }
            }

            i = closeIdx + 2;
        }
        return out.toString();
    }

    private static String marker(String token) {
        return "[[unknown: " + token + "]]";
    }
}
