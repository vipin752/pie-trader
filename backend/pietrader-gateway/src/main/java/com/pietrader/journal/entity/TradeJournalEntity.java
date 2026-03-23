package com.pietrader.journal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

/** PIE TRADER — trade_journal — AI training dataset (contract §10). */
@Entity
@Table(name = "trade_journal", indexes = {
    @Index(name = "idx_tj_symbol",      columnList = "symbol"),
    @Index(name = "idx_tj_trade_id",    columnList = "trade_id"),
    @Index(name = "idx_tj_regime",      columnList = "regime"),
    @Index(name = "idx_tj_recorded_at", columnList = "recorded_at"),
    @Index(name = "idx_tj_closed",      columnList = "is_closed")
})
@Data
public class TradeJournalEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // §10 Contract fields
    @Column(name = "trade_id",   nullable = false, length = 64) private String  tradeId;
    @Column(name = "symbol",     nullable = false, length = 20) private String  symbol;
    @Column(name = "regime",     length = 30)                   private String  regime;
    @Column(name = "gamma")                                     private Double  gamma;
    @Column(name = "iv")                                        private Double  iv;
    @Column(name = "pcr")                                       private Double  pcr;
    @Column(name = "liquidity",  length = 20)                   private String  liquidity;
    @Column(name = "strategy",   length = 50)                   private String  strategy;
    @Column(name = "strike",     length = 60)                   private String  strike;
    @Column(name = "entry")                                     private Double  entry;
    @Column(name = "exit_price")                                private Double  exitPrice;
    @Column(name = "exit_reason",length = 60)                   private String  exitReason;
    @Column(name = "rr")                                        private Double  rr;
    @Column(name = "pnl")                                       private Double  pnl;
    @Column(name = "confidence")                                private Integer confidence;
    @Column(name = "notes",      length = 500)                  private String  notes;
    @Column(name = "market_state_json", columnDefinition = "TEXT") private String marketStateJson;
    @Column(name = "analytics_json",    columnDefinition = "TEXT") private String analyticsJson;

    // Execution context
    @Column(name = "direction",  length = 10)                   private String  direction;
    @Column(name = "lots")                                      private Integer lots;
    @Column(name = "trade_type", length = 20)                   private String  tradeType;
    @Column(name = "mode",       length = 10)                   private String  mode;
    @Column(name = "session_phase", length = 30)                private String  sessionPhase;
    @Column(name = "fear_index")                                private Double  fearIndex;

    @Column(name = "is_closed")                                 private Boolean isClosed = false;
    @Column(name = "recorded_at", nullable = false)             private Long    recordedAt;
    @Column(name = "exit_at")                                   private Long    exitAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
