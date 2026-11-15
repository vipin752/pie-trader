package com.pietrader.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row per close EVENT ID (not per close) — the idempotency guard
 * behind {@link RiskEngineService#onClose}. */
@Entity
@Table(name = "close_event_log")
public class CloseEventEntity {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false) private String underlying;
    @Column(nullable = false) private BigDecimal pnl;
    @Column(name = "applied_at", nullable = false) private LocalDateTime appliedAt;

    protected CloseEventEntity() {}

    public CloseEventEntity(String eventId, String underlying, BigDecimal pnl, LocalDateTime appliedAt) {
        this.eventId = eventId;
        this.underlying = underlying;
        this.pnl = pnl;
        this.appliedAt = appliedAt;
    }
}
