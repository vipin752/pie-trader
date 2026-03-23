package com.pietrader.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_signals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── MARKET CONTEXT ──────────────────────────────
    @Column(name = "symbol")
    private String symbol;

    @Column(name = "spot")
    private Double spot;

    @Column(name = "atm")
    private Integer atm;

    @Column(name = "expiry")
    private String expiry;

    @Column(name = "days_to_expiry")
    private Integer daysToExpiry;

    @Column(name = "phase")
    private String phase;

    // ── TRADE SIGNAL ────────────────────────────────
    @Column(name = "strategy")
    private String strategy;

    @Column(name = "signal_reason")
    private String signalReason;

    @Column(name = "signal_confidence")
    private String signalConfidence;

    // ── AUTO TRADE DECISION ─────────────────────────
    @Column(name = "action")
    private String action;

    @Column(name = "direction")
    private String direction;

    @Column(name = "option_strike")
    private String optionStrike;

    @Column(name = "decision_confidence")
    private String decisionConfidence;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "recommendation_score")
    private Integer recommendationScore;

    @Column(name = "recommendation_level")
    private String recommendationLevel;

    // ── MARKET STRUCTURE ────────────────────────────
    @Column(name = "compression_detected")
    private Boolean compressionDetected;

    @Column(name = "breakout_signal")
    private String breakoutSignal;

    @Column(name = "pressure_label")
    private String pressureLabel;

    // ── VOLATILITY ──────────────────────────────────
    @Column(name = "atm_iv_pct")
    private Double atmIvPct;

    @Column(name = "daily_move")
    private Double dailyMove;

    @Column(name = "weekly_move")
    private Double weeklyMove;

    @Column(name = "upper_1d")
    private Double upper1d;

    @Column(name = "lower_1d")
    private Double lower1d;

    @Column(name = "pcr")
    private Double pcr;

    @Column(name = "pcr_sentiment")
    private String pcrSentiment;

    // ── DEALER / GAMMA ──────────────────────────────
    @Column(name = "dealer_inventory")
    private String dealerInventory;

    @Column(name = "hedging_behavior")
    private String hedgingBehavior;

    @Column(name = "gamma_flip")
    private Double gammaFlip;

    @Column(name = "net_gamma")
    private Double netGamma;

    @Column(name = "gamma_regime")
    private String gammaRegime;

    @Column(name = "call_gamma_wall")
    private Integer callGammaWall;

    @Column(name = "put_gamma_wall")
    private Integer putGammaWall;

    // ── LIQUIDITY ───────────────────────────────────
    @Column(name = "support")
    private Integer support;

    @Column(name = "resistance")
    private Integer resistance;

    @Column(name = "call_wall")
    private Integer callWall;

    @Column(name = "put_wall")
    private Integer putWall;

    @Column(name = "liquidity_sweep_signal")
    private String liquiditySweepSignal;

    // ── FEAR INDEX ──────────────────────────────────
    @Column(name = "fear_index")
    private Double fearIndex;

    @Column(name = "fear_zone")
    private String fearZone;

    @Column(name = "recommended_action")
    private String recommendedAction;

    @Column(name = "fear_pcr")
    private Double fearPcr;

    @Column(name = "fear_iv")
    private Double fearIv;

    @Column(name = "fear_gamma")
    private Double fearGamma;

    @Column(name = "fear_volume")
    private Double fearVolume;

    @Column(name = "fear_skew")
    private Double fearSkew;

    // ── TRADING CARD ────────────────────────────────
    @Column(name = "session_1_card")
    private String session1Card;

    @Column(name = "session_3_card")
    private String session3Card;

    @Column(name = "btst")
    private String btst;

    // ── EXECUTION ───────────────────────────────────
    @Column(name = "trading_window")
    private String tradingWindow;

    @Column(name = "is_fake_breakout")
    private Boolean isFakeBreakout;

    @Column(name = "execution_ready")
    private Boolean executionReady;

    @Column(name = "breakout_status")
    private String breakoutStatus;

    @Column(name = "breakout_trigger_price")
    private String breakoutTriggerPrice;

    @Column(name = "entry_signal")
    private String entrySignal;

    @Column(name = "entry_type")
    private String entryType;

    // ── AUDIT ───────────────────────────────────────
    @Column(name = "signal_timestamp")
    private String signalTimestamp;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
