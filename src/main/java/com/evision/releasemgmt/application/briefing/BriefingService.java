package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingFeed;
import com.evision.releasemgmt.domain.briefing.BriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.ports.BriefingReadPort;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BriefingService {

    private final BriefingReadPort readPort;
    private final BriefingRunRepository runs;
    private final ProductRepository products;

    public BriefingService(BriefingReadPort readPort, BriefingRunRepository runs, ProductRepository products) {
        this.readPort = readPort;
        this.runs = runs;
        this.products = products;
    }

    public List<BriefingFeed> feeds() {
        return Arrays.stream(BriefingTask.values()).map(readPort::feed).toList();
    }

    public List<BriefingOverview> overview() {
        List<Product> all = products.findAll();
        Map<UUID, String> names = all.stream().collect(Collectors.toMap(Product::id, Product::name));
        return Arrays.stream(BriefingTask.values()).map(task -> overview(task, all, names)).toList();
    }

    private BriefingOverview overview(BriefingTask task, List<Product> all, Map<UUID, String> names) {
        BriefingFeed claude = readPort.feed(task);
        if (!task.appFallback()) {
            return new BriefingOverview(claude, List.of(), List.of());
        }
        List<AppBriefingRun> taskRuns = runs.findByTask(task);
        Optional<Instant> claudeWorkingAt = claude.latest()
                .filter(r -> r.status() != BriefingStatus.FAILED)
                .map(BriefingRun::runAt);
        Map<UUID, AppBriefingRun> newestShowable = taskRuns.stream()
                .filter(r -> !AppBriefingRun.REASON_CLAUDE_COVERED.equals(r.reason()))
                .collect(Collectors.toMap(AppBriefingRun::productId, Function.identity(), (newer, older) -> newer));

        // Newest wins: the app only runs when Claude is missing or failed, so this is "Claude first, fallback otherwise".
        List<AppRunView> latest = all.stream()
                .filter(p -> p.archivedAt() == null)
                .map(p -> newestShowable.get(p.id()))
                .filter(r -> r != null && claudeWorkingAt.map(r.finishedAt()::isAfter).orElse(true))
                .map(r -> new AppRunView(r, names.get(r.productId())))
                .toList();
        List<AppRunView> history = taskRuns.stream().map(r -> new AppRunView(r, names.get(r.productId()))).toList();
        return new BriefingOverview(claude, latest, history);
    }
}
