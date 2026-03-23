package com.pietrader.journal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "trade_exit", indexes = {
    @Index(name = "idx_tex_symbol",  columnList = "symbol"),
    @Index(name = "idx_tex_exit_at", columnList = "exit_at")
})
@Data
public class TradeExitEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "symbol",      nullable = false, length = 20) private String symbol;
    @Column(name = "trade_id",    length = 64)                   private String tradeId;
    @Column(name = "order_id",    length = 64)                   private String orderId;
    @Column(name = "exit_price")                                 private Double exitPrice;
    @Column(name = "exit_reason", length = 50)                   private String exitReason;
    @Column(name = "pnl")                                        private Double pnl;
    @Column(name = "exit_at",     nullable = false)              private Long   exitAt;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
}
