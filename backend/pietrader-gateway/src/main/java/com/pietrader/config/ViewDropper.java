package com.pietrader.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Drops views that block Hibernate ddl-auto=update from altering columns.
 * Views are recreated automatically by V3__journal_stats_tables.sql
 * or by the next Flyway migration run.
 *
 * Only active when spring.jpa.hibernate.ddl-auto=update.
 * Runs before Hibernate schema update via @Order(1).
 */
@Configuration
@Order(1)
@ConditionalOnProperty(
	name = "spring.jpa.hibernate.ddl-auto",
	havingValue = "update"
)
@Slf4j
public class ViewDropper {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void dropBlockingViews() {
	try (Connection conn = dataSource.getConnection();
	     Statement stmt = conn.createStatement()) {

	    stmt.execute("DROP VIEW IF EXISTS v_today_pnl CASCADE");
	    stmt.execute("DROP VIEW IF EXISTS v_stats_engine CASCADE");
	    stmt.execute("DROP VIEW IF EXISTS v_win_rate_by_regime CASCADE");
	    stmt.execute("DROP VIEW IF EXISTS v_win_rate_by_strategy CASCADE");
	    stmt.execute("DROP VIEW IF EXISTS v_win_rate_by_session CASCADE");

	    log.info("✅ Blocking views dropped — Hibernate ddl-auto=update can proceed");

	} catch (Exception e) {
	    log.warn("⚠️ Could not drop views (may not exist yet): {}", e.getMessage());
	}
    }
}
