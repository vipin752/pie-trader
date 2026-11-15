package com.pietrader.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Durable day ledger — Postgres is the source of truth so a restart
 * recovers state instead of starting the day's risk budget over. */
@Entity
@Table(name = "day_ledger")
public class DayLedgerEntity {

    @EmbeddedId
    private Key id;

    @Column(name = "realised_pnl", nullable = false)
    private BigDecimal realisedPnl = BigDecimal.ZERO;

    @Column(name = "consecutive_losses", nullable = false)
    private int consecutiveLosses = 0;

    @Column(name = "last_loss_at")
    private LocalDateTime lastLossAt;

    protected DayLedgerEntity() {}

    public DayLedgerEntity(String underlying, LocalDate day) {
        this.id = new Key(underlying, day);
    }

    public Key getId() { return id; }
    public BigDecimal getRealisedPnl() { return realisedPnl; }
    public void setRealisedPnl(BigDecimal v) { this.realisedPnl = v; }
    public int getConsecutiveLosses() { return consecutiveLosses; }
    public void setConsecutiveLosses(int v) { this.consecutiveLosses = v; }
    public LocalDateTime getLastLossAt() { return lastLossAt; }
    public void setLastLossAt(LocalDateTime v) { this.lastLossAt = v; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "underlying")
        private String underlying;
        @Column(name = "trading_day")
        private LocalDate tradingDay;

        protected Key() {}

        public Key(String underlying, LocalDate tradingDay) {
            this.underlying = underlying;
            this.tradingDay = tradingDay;
        }

        public String getUnderlying() { return underlying; }
        public LocalDate getTradingDay() { return tradingDay; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(underlying, k.underlying) && Objects.equals(tradingDay, k.tradingDay);
        }

        @Override
        public int hashCode() { return Objects.hash(underlying, tradingDay); }
    }
}
