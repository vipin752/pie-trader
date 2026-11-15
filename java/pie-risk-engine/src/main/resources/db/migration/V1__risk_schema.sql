-- Durable source of truth for the risk engine (spec §9 audit trail;
-- Critical Safety #4 "persistent position state", #8 "recovery of ... open
-- positions"). Every decision is written here BEFORE it is returned, so a
-- restart can reconstruct the day's state instead of trusting memory.

CREATE TABLE day_ledger (
    underlying           VARCHAR(32) NOT NULL,
    trading_day          DATE        NOT NULL,
    realised_pnl         NUMERIC(14,2) NOT NULL DEFAULT 0,
    consecutive_losses   INT           NOT NULL DEFAULT 0,
    last_loss_at         TIMESTAMP,
    PRIMARY KEY (underlying, trading_day)
);

CREATE TABLE risk_decision_log (
    id            BIGSERIAL PRIMARY KEY,
    underlying    VARCHAR(32)   NOT NULL,
    symbol        VARCHAR(80)   NOT NULL,
    qty           INT           NOT NULL,
    risk_inr      NUMERIC(14,2) NOT NULL,
    allowed       BOOLEAN       NOT NULL,
    reasons       TEXT          NOT NULL,   -- '; '-joined; empty when allowed
    decided_at    TIMESTAMP     NOT NULL,
    recorded_at   TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_risk_decision_log_underlying_time ON risk_decision_log (underlying, decided_at);

CREATE TABLE close_event_log (
    -- Idempotency guard for Critical Safety #2/#3 ("idempotent order
    -- submission", "duplicate event protection"): the same close event
    -- (e.g. a broker fill replayed by Kafka) must only move the ledger once.
    event_id      VARCHAR(100) PRIMARY KEY,
    underlying    VARCHAR(32)   NOT NULL,
    pnl           NUMERIC(14,2) NOT NULL,
    applied_at    TIMESTAMP     NOT NULL DEFAULT now()
);
