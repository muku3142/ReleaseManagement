package com.evision.releasemgmt.adapters.web.dto;

import com.evision.releasemgmt.application.briefing.AppRunView;
import com.evision.releasemgmt.application.briefing.BriefingOverview;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record BriefingsResponse(List<Task> tasks) {

    public record Task(String id, String name, String schedule, boolean readError, Run latest, List<Run> runs,
                       boolean appFallback, List<AppRun> appLatest, List<AppRun> appRuns) {

        public static Task of(BriefingOverview overview) {
            BriefingFeed feed = overview.claude();
            return new Task(feed.task().id(), feed.task().displayName(), feed.task().schedule(), feed.readError(),
                    feed.latest().map(Run::of).orElse(null), feed.runs().stream().map(Run::of).toList(),
                    feed.task().appFallback(), overview.appLatest().stream().map(AppRun::of).toList(),
                    overview.appRuns().stream().map(AppRun::of).toList());
        }
    }

    public record Run(Instant runAt, String status, String headline, Integer attention, String h1, String h2,
                      String h3) {

        public static Run of(BriefingRun r) {
            return new Run(r.runAt(), r.status().wire(), r.headline(), r.attention(), r.h1(), r.h2(), r.h3());
        }
    }

    public record AppRun(UUID id, UUID productId, String productName, Instant runAt, String status, String reason,
                         String trigger, String headline, String summary, Integer attention, List<Section> sections) {

        public static AppRun of(AppRunView view) {
            AppBriefingRun r = view.run();
            return new AppRun(r.id(), r.productId(), view.productName(), r.finishedAt(), r.status().wire(),
                    r.reason(), r.trigger().name().toLowerCase(Locale.ROOT), r.headline(), r.summary(),
                    r.attention(), r.sections().stream().map(Section::of).toList());
        }
    }

    public record Section(String title, List<Item> items) {

        static Section of(BriefingContent.Section s) {
            return new Section(s.title(), s.items().stream().map(i -> new Item(i.issueKey(), i.text())).toList());
        }
    }

    public record Item(String key, String text) { }
}
