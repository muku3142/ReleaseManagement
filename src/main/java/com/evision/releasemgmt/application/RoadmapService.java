package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RiceCalculation;
import com.evision.releasemgmt.domain.roadmap.RiceEdits;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapFilter;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemDetails;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRanking;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import com.evision.releasemgmt.ports.PortfolioReadRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.RoadmapHistoryRepository;
import com.evision.releasemgmt.ports.RoadmapRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoadmapService {

    private final RoadmapRepository items;
    private final RoadmapHistoryRepository history;
    private final ProductRepository products;
    private final PortfolioReadRepository portfolio;
    private final Clock clock;

    public RoadmapService(RoadmapRepository items, RoadmapHistoryRepository history, ProductRepository products,
                          PortfolioReadRepository portfolio, Clock clock) {
        this.items = items;
        this.history = history;
        this.products = products;
        this.portfolio = portfolio;
        this.clock = clock;
    }

    // The one ordering shared by the list, the CSV and the Phase 20 planner.
    public List<RankedItem> ranked(RoadmapFilter filter) {
        return RoadmapRanking.rank(items.findMatching(filter), portfolio.runningClientCounts());
    }

    public RankedItem unranked(RoadmapItem item) {
        return RoadmapRanking.unranked(item, portfolio.runningClientCounts().getOrDefault(item.productId(), 0));
    }

    public RoadmapItem get(UUID itemId) {
        return items.findById(itemId).orElseThrow(RoadmapItemNotFoundException::new);
    }

    public RoadmapItem addManual(String title, UUID productId, String actor) {
        requireLinkable(productId, null);
        return items.add(RoadmapItem.manual(title.strip(), productId, actor, clock.instant()));
    }

    public RoadmapItem setInputs(UUID itemId, RiceInputs requested, String actor) {
        Instant now = clock.instant();
        return items.updateInputsUnderLock(itemId, current -> RiceEdits.entered(current, requested, actor, now));
    }

    public RoadmapItem override(UUID itemId, RiceField field, BigDecimal value, String reason, String actor) {
        Map<UUID, Integer> counts = portfolio.runningClientCounts();
        Instant now = clock.instant();
        String stripped = reason.strip();
        return items.updateInputsUnderLock(itemId, current -> RiceEdits.override(current,
                calculated(current, counts), field, value, stripped, actor, now));
    }

    public RoadmapItem resetOverride(UUID itemId, RiceField field, String actor) {
        Map<UUID, Integer> counts = portfolio.runningClientCounts();
        Instant now = clock.instant();
        return items.updateInputsUnderLock(itemId, current -> RiceEdits.reset(current,
                calculated(current, counts), field, actor, now));
    }

    private static RiceInputs calculated(RoadmapItem item, Map<UUID, Integer> counts) {
        return RiceCalculation.calculated(item, counts.getOrDefault(item.productId(), 0));
    }

    public RoadmapItem updateDetails(UUID itemId, RoadmapItemDetails details) {
        RoadmapItem current = get(itemId);
        requireLinkable(details.productId(), current.productId());
        String title = current.isJira() || details.title() == null ? null : details.title().strip();
        return items.updateDetails(itemId, new RoadmapItemDetails(title, details.status(), details.productId(),
                blankToNull(details.targetQuarter()), blankToNull(details.owner()), blankToNull(details.notes())));
    }

    public void archive(UUID itemId) {
        if (!get(itemId).archived()) {
            items.setArchivedAt(itemId, clock.instant());
        }
    }

    public void restore(UUID itemId) {
        if (get(itemId).archived()) {
            items.setArchivedAt(itemId, null);
        }
    }

    public List<InputChange> history(UUID itemId) {
        get(itemId);
        return history.historyOf(itemId);
    }

    // D-15-07: an existing link to a since-archived product stays; only a new link must be to a live product.
    private void requireLinkable(UUID productId, UUID currentProductId) {
        if (productId.equals(currentProductId)) {
            return;
        }
        if (products.findById(productId).orElseThrow(ProductNotFoundException::new).archived()) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_PRODUCT_ARCHIVED);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
