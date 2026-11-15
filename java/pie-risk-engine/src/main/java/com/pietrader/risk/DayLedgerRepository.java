package com.pietrader.risk;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plain repository — the earlier version here used a @Lock(PESSIMISTIC_WRITE)
 * override on findById, which turned out to be the wrong tool: Hibernate
 * issuing SELECT ... FOR UPDATE inside a transaction that later needed a
 * read-only path threw UnexpectedRollbackException in practice (caught by
 * the Testcontainers-backed IT suite, not guessed — see qa/defect-register
 * DEF-002). Cross-replica serialisation for a correlation group is handled
 * by {@link PortfolioLock} (Redis) instead, which covers single-underlying
 * concurrency too since one underlying's group always includes itself.
 */
public interface DayLedgerRepository extends JpaRepository<DayLedgerEntity, DayLedgerEntity.Key> {}
