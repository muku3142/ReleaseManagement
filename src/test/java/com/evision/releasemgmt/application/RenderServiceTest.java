package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.render.MustacheFlexmarkRenderAdapter;
import com.evision.releasemgmt.domain.template.RenderBoundExceededException;
import com.evision.releasemgmt.domain.template.TemplateMalformedException;
import com.evision.releasemgmt.domain.template.view.CommentView;
import com.evision.releasemgmt.domain.template.view.ItemView;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import com.evision.releasemgmt.domain.template.view.SampleReleaseView;
import com.evision.releasemgmt.ports.RenderEnginePort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderServiceTest {

    private final RenderService service =
            new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(2), 500, 64);

    @Test
    void unknownVariableRendersAsNamedMarker() {
        RenderResult result = service.render("{{ticket.compnent}}", SampleReleaseView.instance(), "Body");
        assertThat(result.output()).contains("[[unknown: ticket.compnent]]");
        assertThat(result.unknownTokens()).containsExactly("ticket.compnent");
    }

    @Test
    void sampleViewRendersLinkedCodeForOneTicketAndNoLinkedCodeForAnother() {
        RenderResult result = service.render(
                "{{#items}}{{#commits}}{{id}}{{/commits}}{{^commits}}No linked code{{/commits}}{{/items}}",
                SampleReleaseView.instance(), "Body");
        assertThat(result.output()).contains("a1b2c3d").contains("No linked code");
    }

    @Test
    void unknownSectionRendersMarkerAndKeepsEnclosedContentVisible() {
        RenderResult result = service.render("{{#nope}}inner{{/nope}}", SampleReleaseView.instance(), "Body");
        assertThat(result.output()).contains("[[unknown: nope]]inner");
    }

    @Test
    void partialPropagatesScannerRejection() {
        assertThatThrownBy(() -> service.render("{{>partial}}", SampleReleaseView.instance(), "Body"))
                .isInstanceOf(TemplateMalformedException.class);
    }

    @Test
    void tooManyItemsTripsTheCollectionCap() {
        RenderService capped = new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(2), 3, 64);
        ReleaseView view = releaseViewWithItems(4);
        assertThatThrownBy(() -> capped.render("{{release.name}}", view, "Body"))
                .isInstanceOf(RenderBoundExceededException.class);
    }

    @Test
    void tooManyCommentsOnOneItemTripsTheCollectionCap() {
        RenderService capped = new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(2), 3, 64);
        List<CommentView> comments = List.of(
                new CommentView("one"), new CommentView("two"),
                new CommentView("three"), new CommentView("four"));
        ItemView item = new ItemView("Title", false, "KEY-1", "Bug", "Open", "", "", "", "", "", "",
                comments, List.of(), List.of());
        ReleaseView view = new ReleaseView("R", "1.0", "OPEN", "", "", "", "",
                List.of(item), List.of());
        assertThatThrownBy(() -> capped.render("{{release.name}}", view, "Body"))
                .isInstanceOf(RenderBoundExceededException.class);
    }

    @Test
    void aRenderThatExceedsTheTimeoutTripsTheWallClockBound() {
        RenderEnginePort slowEngine = new RenderEnginePort() {
            @Override
            public String render(String source, Map<String, Object> context) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "never used";
            }

            @Override
            public String resolve(String source, Map<String, Object> context) {
                return source;
            }
        };
        RenderService slow = new RenderService(slowEngine, Duration.ofMillis(1), 500, 64);
        assertThatThrownBy(() -> slow.render("{{release.name}}", SampleReleaseView.instance(), "Body"))
                .isInstanceOf(RenderBoundExceededException.class);
    }

    // WR-05: jmustache does not honour interruption, so a render that trips the timeout keeps
    // running in the background - this proves that orphan is still capped by maxConcurrentRenders
    // rather than able to accumulate unboundedly: a second request is refused outright while the
    // first is still actually running, not merely while its caller was still waiting.
    @Test
    void aSecondRenderIsRefusedWhileAnOrphanedTimedOutRenderIsStillRunning() throws InterruptedException {
        BlockingRenderEngine engine = new BlockingRenderEngine();
        RenderService capped = new RenderService(engine, Duration.ofMillis(50), 500, 1);

        assertThatThrownBy(() -> capped.render("{{release.name}}", SampleReleaseView.instance(), "Body"))
                .isInstanceOf(RenderBoundExceededException.class);
        assertThat(engine.started.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> capped.render("{{release.name}}", SampleReleaseView.instance(), "Body"))
                .isInstanceOf(RenderBoundExceededException.class);

        engine.release.countDown();
    }

    private static final class BlockingRenderEngine implements RenderEnginePort {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String render(String source, Map<String, Object> context) {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                // jmustache does not honour interruption either - this fake mirrors that so the
                // background render keeps "running" past its caller's timeout, exactly like WR-05.
            }
            return "unused";
        }

        @Override
        public String resolve(String source, Map<String, Object> context) {
            return source;
        }
    }

    @Test
    void resolveTextEnforcesTheCollectionCap() {
        RenderService capped = new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(2), 3, 64);
        ReleaseView view = releaseViewWithItems(4);
        assertThatThrownBy(() -> capped.resolveText("{{release.name}}", view, "Header"))
                .isInstanceOf(RenderBoundExceededException.class);
    }

    @Test
    void resolveTextMarksUnknownTokensLikeRenderDoes() {
        RenderResult result = service.resolveText("{{nope}}", SampleReleaseView.instance(), "Header text");
        assertThat(result.output()).isEqualTo("[[unknown: nope]]");
        assertThat(result.unknownTokens()).containsExactly("nope");
    }

    @Test
    void contextMapperProducesOnlyPlainLeafAndContainerTypes() {
        Map<String, Object> context = RenderContextMapper.toContext(SampleReleaseView.instance());
        assertOnlyPlainData(context);
    }

    private static ReleaseView releaseViewWithItems(int count) {
        List<ItemView> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new ItemView("Title " + i, false, "KEY-" + i, "Bug", "Open", "", "", "", "", "", "",
                    List.of(), List.of(), List.of()));
        }
        return new ReleaseView("R", "1.0", "OPEN", "", "", "", "", items, List.of());
    }

    private static void assertOnlyPlainData(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(RenderServiceTest::assertOnlyPlainData);
        } else if (value instanceof List<?> list) {
            list.forEach(RenderServiceTest::assertOnlyPlainData);
        } else if (value instanceof String || value instanceof Boolean) {
            // leaf - acceptable
        } else {
            throw new AssertionError("Context contains a non-plain-data leaf: "
                    + (value == null ? "null" : value.getClass()));
        }
    }
}
