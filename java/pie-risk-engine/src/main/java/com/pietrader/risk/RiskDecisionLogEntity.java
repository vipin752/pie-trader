package com.pietrader.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Append-only audit row for every decision — approved or rejected
 * (spec §9, §14.10: every decision must be explainable and auditable). */
@Entity
@Table(name = "risk_decision_log")
public class RiskDecisionLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String underlying;
    @Column(nullable = false) private String symbol;
    @Column(nullable = false) private int qty;
    @Column(name = "risk_inr", nullable = false) private BigDecimal riskInr;
    @Column(nullable = false) private boolean allowed;
    @Column(nullable = false) private String reasons;
    @Column(name = "decided_at", nullable = false) private LocalDateTime decidedAt;

    protected RiskDecisionLogEntity() {}

    public RiskDecisionLogEntity(String underlying, String symbol, int qty, BigDecimal riskInr,
                                 boolean allowed, String reasons, LocalDateTime decidedAt) {
        this.underlying = underlying;
        this.symbol = symbol;
        this.qty = qty;
        this.riskInr = riskInr;
        this.allowed = allowed;
        this.reasons = reasons;
        this.decidedAt = decidedAt;
    }
}
