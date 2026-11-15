package com.pietrader.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Deterministic Risk Engine (spec §13, §14.10).
 *
 * <p>This is the one piece the master spec insists stays Java, never Python:
 * "Java must remain responsible for deterministic execution and risk
 * enforcement. Python analytics must not bypass the Java risk approval
 * process." Everything else in this repository today (structure, SMC,
 * regime classification, backtesting) is still the Python research core in
 * {@code pie_trader/}; this module is the first real port of its
 * deterministic half ({@code pie_trader.rules.Guard}) onto the stack the
 * production system is meant to run on.
 *
 * <p>Postgres holds the durable day ledger and the decision/close audit
 * trail (Critical Safety #4/#8); a short-lived Redis lock keeps two
 * replicas from deciding on the same correlated signal at once (#5/#7).
 * What's still a stand-in: no Kafka wiring, no real broker reconciliation,
 * no Position Management service — see docs/ARCHITECTURE.md and qa/ for the
 * honest status of each.
 */
@SpringBootApplication
@EnableConfigurationProperties(RiskRules.class)
public class PieRiskEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PieRiskEngineApplication.class, args);
    }
}
