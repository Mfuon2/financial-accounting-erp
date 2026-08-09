package com.qesuite.accounting.banking.repository

import com.qesuite.accounting.banking.domain.BankStatementLine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

/**
 * Line-level repository, used directly (not only via [BankStatementImportRepository]'s cascade)
 * because match/unmatch/ignore/auto-match all act on a single line by its own id — mirrors why
 * this module has a dedicated line repository at all, unlike some header+lines modules that only
 * ever mutate lines through the parent.
 */
@Repository
interface BankStatementLineRepository : JpaRepository<BankStatementLine, UUID> {

    /**
     * Fetch-joins the parent [com.qesuite.accounting.banking.domain.BankStatementImport] so
     * `BankStatementLine.entityId`/`.accountId`/`.bankStatementImportId` (all read off that lazy
     * `@ManyToOne`) are safe to access from the controller layer — this app runs with
     * `spring.jpa.open-in-view: false` (application.yml §2.4), so a plain `findById` would leave
     * the parent as an uninitialized proxy that throws `LazyInitializationException` the moment
     * the controller reads it after the service's read-only transaction has already closed. Used
     * by every id-scoped controller endpoint that needs the line's owning entity for the
     * IDOR ownership check ([com.qesuite.accounting.shared.security.SecurityUtils.requireOwnEntity]).
     */
    @Query("SELECT l FROM BankStatementLine l JOIN FETCH l.bankStatementImport WHERE l.id = :id")
    fun findByIdWithImport(id: UUID): Optional<BankStatementLine>
}
