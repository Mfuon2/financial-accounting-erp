package com.qesuite.accounting.journal.service

import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service
class AdjustmentService(
    private val journalService: JournalService,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
) {

    private val log = LoggerFactory.getLogger(AdjustmentService::class.java)

    private companion object {
        const val MONEY_SCALE = 6
        val ROUND = RoundingMode.HALF_EVEN
    }

    @Transactional
    fun recordAccrual(command: CreateJournalEntryCommand): JournalEntry {
        // §6.1 — Specialized logic for identifying accruals could be added here
        return journalService.createEntry(command.copy(sourceType = "ACCRUAL"))
    }

    @Transactional
    fun recordDeferral(command: CreateJournalEntryCommand): JournalEntry {
        // §6.1 — Specialized logic for identifying deferrals could be added here
        return journalService.createEntry(command.copy(sourceType = "DEFERRAL"))
    }

    /**
     * §6.2 — Amortize prepaid expenses for the given entity and period.
     *
     * For every account with subtype PREPAID_EXPENSES that carries a positive balance,
     * generates and immediately posts an adjusting journal entry:
     *
     *   DR  Operating Expenses  (amount = prepaid.currentBalance)
     *   CR  Prepaid Account     (amount = prepaid.currentBalance)
     *
     * Entries are processed in batch. A failure on any individual account is caught,
     * logged, and the loop continues so that one bad account cannot abort the entire run.
     */
    @Transactional
    fun amortizePrepayments(entityId: UUID, periodId: UUID) {
        // 1. Resolve functional currency — required for journal line currencyCode.
        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId)
            .orElseThrow {
                ValidationException(
                    errorCode = "FUNCTIONAL_CURRENCY_NOT_SET",
                    message = "No functional currency configured for entity $entityId.",
                    context = mapOf("entity_id" to entityId),
                )
            }
        val currencyCode = functionalCurrency.currencyCode

        // 2. Find all accounts for the entity and filter down to prepaid accounts
        //    that still carry a positive balance.
        val prepaidAccounts = accountRepository.findAllByEntityId(entityId)
            .filter { it.accountSubtype == AccountSubtype.CURRENT_PREPAID && it.currentBalance.signum() > 0 }

        if (prepaidAccounts.isEmpty()) {
            log.info("amortizePrepayments: no CURRENT_PREPAID accounts with positive balance for entity {}", entityId)
            return
        }

        // 3. Find the target expense account to debit.
        val expenseAccount = accountRepository.findAllByEntityId(entityId)
            .firstOrNull { it.accountSubtype == AccountSubtype.OPERATING_EXPENSES }
            ?: throw ValidationException(
                errorCode = "MISSING_EXPENSE_ACCOUNT",
                message = "No OPERATING_EXPENSES account found for entity $entityId.",
                context = mapOf("entity_id" to entityId),
            )

        val today = LocalDate.now()

        // 4. For each prepaid account, generate and post the amortization entry.
        prepaidAccounts.forEach { prepaid ->
            try {
                val balance = prepaid.currentBalance.setScale(MONEY_SCALE, ROUND)

                val jeLines = listOf(
                    // DR Operating Expenses
                    CreateJournalLineCommand(
                        accountId    = expenseAccount.id,
                        description  = "Amortize prepaid: ${prepaid.accountName}",
                        debitAmount  = balance,
                        creditAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUND),
                        currencyCode = currencyCode,
                        exchangeRate = BigDecimal.ONE,
                    ),
                    // CR Prepaid Account
                    CreateJournalLineCommand(
                        accountId    = prepaid.id,
                        description  = "Amortize prepaid: ${prepaid.accountName}",
                        debitAmount  = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUND),
                        creditAmount = balance,
                        currencyCode = currencyCode,
                        exchangeRate = BigDecimal.ONE,
                    ),
                )

                val entry = journalService.createEntry(
                    CreateJournalEntryCommand(
                        entityId    = entityId,
                        periodId    = periodId,
                        transDate   = today,
                        description = "Amortize prepaid: ${prepaid.accountName}",
                        sourceType  = "PREPAID_AMORTIZATION",
                        sourceId    = prepaid.id,
                        lines       = jeLines,
                    )
                )
                journalService.postEntryAsSystem(entry.id)

                log.info(
                    "amortizePrepayments: posted entry {} for account {} (balance={})",
                    entry.id, prepaid.accountCode, balance,
                )
            } catch (ex: Exception) {
                log.error(
                    "amortizePrepayments: failed for account {} (entityId={}, periodId={}): {}",
                    prepaid.accountCode, entityId, periodId, ex.message, ex,
                )
            }
        }
    }

    /**
     * §6.2 — Recognize unearned (deferred) revenue for the given entity and period.
     *
     * For every account with subtype CURRENT_DEFERRED_REVENUE that carries a positive
     * balance, generates and immediately posts an adjusting journal entry:
     *
     *   DR  Deferred Revenue account   (amount = deferred.currentBalance)
     *   CR  Operating Revenue account  (amount = deferred.currentBalance)
     *
     * Entries are processed in batch. Individual failures are caught, logged, and the
     * loop continues so that one bad account cannot abort the entire run.
     */
    @Transactional
    fun recognizeUnearnedRevenue(entityId: UUID, periodId: UUID) {
        // 1. Resolve functional currency.
        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId)
            .orElseThrow {
                ValidationException(
                    errorCode = "FUNCTIONAL_CURRENCY_NOT_SET",
                    message = "No functional currency configured for entity $entityId.",
                    context = mapOf("entity_id" to entityId),
                )
            }
        val currencyCode = functionalCurrency.currencyCode

        // 2. Find all deferred-revenue accounts with a positive balance.
        val deferredAccounts = accountRepository.findAllByEntityId(entityId)
            .filter { it.accountSubtype == AccountSubtype.CURRENT_DEFERRED_REVENUE && it.currentBalance.signum() > 0 }

        if (deferredAccounts.isEmpty()) {
            log.info(
                "recognizeUnearnedRevenue: no CURRENT_DEFERRED_REVENUE accounts with positive balance for entity {}",
                entityId,
            )
            return
        }

        // 3. Find the target revenue account to credit.
        val revenueAccount = accountRepository.findAllByEntityId(entityId)
            .firstOrNull { it.accountSubtype == AccountSubtype.OPERATING_REVENUE }
            ?: throw ValidationException(
                errorCode = "MISSING_REVENUE_ACCOUNT",
                message = "No OPERATING_REVENUE account found for entity $entityId.",
                context = mapOf("entity_id" to entityId),
            )

        val today = LocalDate.now()

        // 4. For each deferred account, generate and post the recognition entry.
        deferredAccounts.forEach { deferredAccount ->
            try {
                val balance = deferredAccount.currentBalance.setScale(MONEY_SCALE, ROUND)

                val jeLines = listOf(
                    // DR Deferred Revenue (liability decreases)
                    CreateJournalLineCommand(
                        accountId    = deferredAccount.id,
                        description  = "Recognize deferred revenue: ${deferredAccount.accountName}",
                        debitAmount  = balance,
                        creditAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUND),
                        currencyCode = currencyCode,
                        exchangeRate = BigDecimal.ONE,
                    ),
                    // CR Operating Revenue (revenue earned)
                    CreateJournalLineCommand(
                        accountId    = revenueAccount.id,
                        description  = "Recognize deferred revenue: ${deferredAccount.accountName}",
                        debitAmount  = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUND),
                        creditAmount = balance,
                        currencyCode = currencyCode,
                        exchangeRate = BigDecimal.ONE,
                    ),
                )

                val entry = journalService.createEntry(
                    CreateJournalEntryCommand(
                        entityId    = entityId,
                        periodId    = periodId,
                        transDate   = today,
                        description = "Recognize deferred revenue: ${deferredAccount.accountName}",
                        sourceType  = "REVENUE_RECOGNITION",
                        sourceId    = deferredAccount.id,
                        lines       = jeLines,
                    )
                )
                journalService.postEntryAsSystem(entry.id)

                log.info(
                    "recognizeUnearnedRevenue: posted entry {} for account {} (balance={})",
                    entry.id, deferredAccount.accountCode, balance,
                )
            } catch (ex: Exception) {
                log.error(
                    "recognizeUnearnedRevenue: failed for account {} (entityId={}, periodId={}): {}",
                    deferredAccount.accountCode, entityId, periodId, ex.message, ex,
                )
            }
        }
    }
}
