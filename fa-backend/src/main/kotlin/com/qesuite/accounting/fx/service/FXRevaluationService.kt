package com.qesuite.accounting.fx.service

import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.fx.domain.RateType
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

@Service
class FXRevaluationService(
    private val accountRepository: AccountRepository,
    private val exchangeRateService: ExchangeRateService,
    private val journalService: JournalService,
    private val currencyRepository: CurrencyRepository
) {

    @Transactional
    fun runRevaluation(entityId: UUID, periodId: UUID, date: LocalDate, gainLossAccountId: UUID) {
        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId)
            .orElseThrow { ValidationException("FUNCTIONAL_CURRENCY_NOT_SET", "Functional currency not set for entity.") }

        val accounts = accountRepository.findAllByEntityId(entityId)
            .filter { it.accountSubtype.isMonetary && it.currencyCode != functionalCurrency.currencyCode }

        accounts.forEach { account ->
            val closingRate = exchangeRateService.getRate(
                entityId, account.currencyCode, functionalCurrency.currencyCode, date, RateType.CLOSING
            )

            // §13.2 — Re-translate monetary item at closing rate
            val targetFunctionalBalance = account.originalCurrencyBalance.multiply(closingRate)
                .setScale(6, RoundingMode.HALF_EVEN)
            val currentFunctionalBalance = account.currentBalance
            val diff = targetFunctionalBalance.subtract(currentFunctionalBalance)
                .setScale(6, RoundingMode.HALF_EVEN)

            if (diff.abs() > BigDecimal("0.000001")) {
                postRevaluationEntry(
                    account, gainLossAccountId, periodId, date, diff, functionalCurrency.currencyCode
                )
            }
        }
    }

    /**
     * D6 — Post a revaluation journal entry applying IAS 21 directional logic.
     *
     * diff > 0 means the functional-currency balance should increase.
     * diff < 0 means the functional-currency balance should decrease.
     *
     * DEBIT-normal (ASSET):
     *   diff > 0 → asset value increased (FX gain): DR account / CR gain-loss
     *   diff < 0 → asset value decreased (FX loss): CR account / DR gain-loss
     *
     * CREDIT-normal (LIABILITY, EQUITY, REVENUE):
     *   diff > 0 → liability/equity balance increased (FX loss for entity): CR account / DR gain-loss
     *   diff < 0 → liability/equity balance decreased (FX gain for entity): DR account / CR gain-loss
     */
    private fun postRevaluationEntry(
        account: com.qesuite.accounting.coa.domain.Account,
        gainLossAccountId: UUID,
        periodId: UUID,
        date: LocalDate,
        diff: BigDecimal,   // positive = functional balance should increase, negative = should decrease
        functionalCurrencyCode: String
    ) {
        val isDebitNormal = account.normalBalance == NormalBalance.DEBIT

        val accountDebit: BigDecimal
        val accountCredit: BigDecimal
        val glDebit: BigDecimal
        val glCredit: BigDecimal

        if (isDebitNormal) {
            // ASSET: diff > 0 = FX gain (asset worth more), diff < 0 = FX loss
            accountDebit  = if (diff.signum() > 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)
            accountCredit = if (diff.signum() < 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)
            glDebit       = if (diff.signum() < 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)  // loss
            glCredit      = if (diff.signum() > 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)  // gain
        } else {
            // LIABILITY / EQUITY: diff > 0 = balance increased = FX loss; diff < 0 = balance decreased = FX gain
            accountDebit  = if (diff.signum() < 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)  // decrease liability
            accountCredit = if (diff.signum() > 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)  // increase liability
            glDebit       = if (diff.signum() > 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)  // FX loss
            glCredit      = if (diff.signum() < 0) diff.abs().setScale(6, RoundingMode.HALF_EVEN) else BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)  // FX gain
        }

        val command = CreateJournalEntryCommand(
            entityId    = account.entityId,
            periodId    = periodId,
            transDate   = date,
            description = "FX Revaluation – ${account.accountName} (IAS 21)",
            sourceType  = "FX_REVALUATION",
            sourceId    = account.id,
            lines = listOf(
                CreateJournalLineCommand(
                    accountId    = account.id,
                    description  = "FX Adjustment – ${account.accountName}",
                    debitAmount  = accountDebit,
                    creditAmount = accountCredit,
                    currencyCode = functionalCurrencyCode
                ),
                CreateJournalLineCommand(
                    accountId    = gainLossAccountId,
                    description  = "FX Gain/Loss – ${account.accountName}",
                    debitAmount  = glDebit,
                    creditAmount = glCredit,
                    currencyCode = functionalCurrencyCode
                )
            )
        )
        val entry = journalService.createDraft(command)
        journalService.postEntryAsSystem(entry.id)
    }
}
