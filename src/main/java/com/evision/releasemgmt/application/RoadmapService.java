package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapFilter;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemDetails;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRanking;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.RoadmapHistoryRepository;
import com.evision.releasemgmt.ports.RoadmapRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class RoadmapService {

    private final RoadmapRepository items;
    private final RoadmapHistoryRepository history;
    private final ProductRepository products;
    private final Clock clock;

    public RoadmapService(
            RoadmapRepository items, RoadmapHistoryRepository history, ProductRepository products, Clock clock) {
        this.items = items;
        this.history = history;
        this.products = products;
        this.clock = clock;
    }

    // The one ordering shared by the list, the CSV and the Phase 20 planner.
    public List<RankedItem> ranked(RoadmapFilter filter) {
        return RoadmapRanking.rank(items.findAll().stream().filter(filter::matches).toList());
    }

    public RoadmapItem get(UUID itemId) {
        return items.findById(itemId).orElseThrow(RoadmapItemNotFoundException::new);
    }

    public RoadmapItem addManual(String title, UUID productId, String actor) {
        requireLinkable(productId, null);
        return items.add(RoadmapItem.manual(title.strip(), productId, actor, clock.instant()));
    }

    public RoadmapItem setInputs(UUID itemId, RiceInputs inputs, String actor) {
        return items.setInputsUnderLock(itemId, current -> {
            if (current.archived()) {
                throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_ITEM_ARCHIVED);
            }
            return inputs;
        }, actor, clock.instant());
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
        if (productId == null || productId.equals(currentProductId)) {
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
