package com.evision.releasemgmt.adapters.persistence.entity;

import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.RiceField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roadmap_item_input_history")
public class RoadmapInputHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false)
    private RiceField field;

    @Column(name = "old_value")
    private BigDecimal oldValue;

    @Column(name = "new_value")
    private BigDecimal newValue;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    protected RoadmapInputHistoryEntity() {
    }

    public static RoadmapInputHistoryEntity from(InputChange change) {
        RoadmapInputHistoryEntity e = new RoadmapInputHistoryEntity();
        e.id = UUID.randomUUID();
        e.itemId = change.itemId();
        e.field = change.field();
        e.oldValue = change.oldValue();
        e.newValue = change.newValue();
        e.changedAt = change.changedAt();
        e.changedBy = change.changedBy();
        return e;
    }

    public InputChange toDomain() {
        return new InputChange(itemId, field, oldValue, newValue, changedAt, changedBy);
    }
}
