package com.qesuite.accounting.payments.domain

/**
 * §14 (Module 14) — Supported payment methods.
 */
enum class PaymentMethod {
    MPESA,
    BANK_TRANSFER,
    CASH,
    CHEQUE,
    CREDIT_CARD,
    DEBIT_NOTE
}
