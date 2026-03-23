package com.pietrader.journal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "trade_execution", indexes = {
    @Index(name = "idx_te_symbol",    columnList = "symbol"),
    @Index(name = "idx_te_order_id",  columnList = "order_id"),
    @Index(name = "idx_te_entry_time",columnList = "entry_time")
})
@Data
public class TradeExecutionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "trade_id",  nullable = false, length = 64) private String  tradeId;
    @Column(name = "order_id",  length = 64)                   private String  orderId;
    @Column(name = "symbol",    nullable = false, length = 20) private String  symbol;
    @Column(name = "strike",    length = 50)                   private String  strike;
    @Column(name = "direction", length = 10)                   private String  direction;
    @Column(name = "lots")                                     private Integer lots;
    @Column(name = "quantity")                                 private Integer quantity;
    @Column(name = "entry_price")                              private Double  entryPrice;
    @Column(name = "sl")                                       private Double  sl;
    @Column(name = "target")                                   private Double  target;
    @Column(name = "confidence")                               private Integer confidence;
    @Column(name = "regime",    length = 30)                   private String  regime;
    @Column(name = "trade_type",length = 20)                   private String  tradeType;
    @Column(name = "mode",      length = 10)                   private String  mode;
    @Column(name = "strategy",  length = 50)                   private String  strategy;
    @Column(name = "status",    length = 20)                   private String  status;
    @Column(name = "failure_reason", length = 200)             private String  failureReason;
    @Column(name = "entry_time", nullable = false)             private Long    entryTime;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
}
