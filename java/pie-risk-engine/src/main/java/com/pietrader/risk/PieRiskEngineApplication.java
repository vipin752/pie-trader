package com.pietrader.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * <p>State here is in-memory and single-instance — a stand-in for the
 * eventual Postgres/Redis-backed, multi-instance version. Treat this as a
 * proof that the Java 25 + Spring Boot toolchain decision actually builds
 * and passes tests, not as something to point real orders at yet.
 */
@SpringBootApplication
public class PieRiskEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PieRiskEngineApplication.class, args);
    }
}
